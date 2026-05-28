package core.client;

import com.google.protobuf.ByteString;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;
import core.message.MessageUtil;
import lombok.Getter;
import lombok.Setter;
import org.sunrise.game.config.ConfigReader;
import org.sunrise.game.core.message.MessageType;
import org.sunrise.game.core.message.SocketMessage;
import org.sunrise.game.genProto.gen.ItemProto;
import org.sunrise.game.genProto.gen.LoginProto;
import org.sunrise.game.genProto.gen.TopicProto;
import org.sunrise.game.log.LogCore;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import io.netty.channel.Channel;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;

/**
 * 压测管理器 - 分阶段统计耗时，支持多人轮询发包测 TPS
 */
public class StressManager {

    public enum PacketMode {
        PING,
        BUSINESS_GET_ITEM_LIST
    }

    private static final ConcurrentHashMap<String, StressClientInfo> clients = new ConcurrentHashMap<>();
    private static final Set<String> currentBatchUids = ConcurrentHashMap.newKeySet();
    private static final AtomicInteger clientCounter = new AtomicInteger(0);
    private static final ExecutorService workerPool = Executors.newFixedThreadPool(16);
    private static final ScheduledExecutorService timeoutScheduler = Executors.newSingleThreadScheduledExecutor();
    /** 登录后定时 C2S_ClientPing，避免服务端 60s 无心跳踢下线 */
    private static final ScheduledExecutorService keepaliveScheduler = Executors.newSingleThreadScheduledExecutor();
    private static final long KEEPALIVE_PING_INITIAL_SEC = 5;
    private static final long KEEPALIVE_PING_INTERVAL_SEC = 10;
    private static final String uidPrefix = "stress";
    private static final long PACKET_STRESS_TIMEOUT_MINUTES = 30;
    /** 单连接未回包上限，避免写缓冲/服务端队列被打满导致丢包、永远收不齐 */
    private static final int MAX_INFLIGHT_PER_CLIENT = 64;
    /** 大包量压测时发送阶段的进度日志间隔（秒） */
    private static final long PACKET_SEND_PROGRESS_SEC = 5;
    /** 发送完成后若仍未收齐，超过该时间才补打一条等待日志 */
    private static final long PACKET_PROGRESS_LOG_AFTER_MS = 30_000;
    /** 收包无进展时重复诊断日志间隔（秒） */
    private static final long PACKET_STALL_DIAGNOSTIC_SEC = 10;
    /** 判定收包停滞：连续多少秒 received 不增长 */
    private static final long PACKET_STALL_THRESHOLD_SEC = 10;

    /** 当前发包压测会话（以收到服务器回包为准统计） */
    private static volatile PacketStressSession packetSession;

    private static volatile boolean running = false;
    private static volatile int currentBatchSize = 0;
    private static final AtomicInteger batchAddressDone = new AtomicInteger(0);
    private static final AtomicInteger batchAddressSuccess = new AtomicInteger(0);
    private static final AtomicInteger batchLoginDone = new AtomicInteger(0);
    private static final AtomicInteger batchLoginSuccess = new AtomicInteger(0);
    private static volatile long phase1StartTime;
    private static volatile long phase2StartTime;
    private static volatile int currentBatchLoginExpected;

    @Setter
    private static Consumer<String> logCallback;
    @Setter
    private static Consumer<Stats> statsCallback;

    @Getter
    public static class Stats {
        private final int total;
        private final int addressFetched;
        private final int loginSuccess;
        private final int loginFailed;
        private final int connected;

        public Stats(int total, int addressFetched, int loginSuccess, int loginFailed, int connected) {
            this.total = total;
            this.addressFetched = addressFetched;
            this.loginSuccess = loginSuccess;
            this.loginFailed = loginFailed;
            this.connected = connected;
        }
    }

    @Getter
    @Setter
    public static class StressClientInfo {
        private final String uid;
        private final SocketClient client;
        private LoginManager.ExternalAddress address;
        private volatile boolean addressFetched;
        private volatile boolean addressFailed;
        private volatile boolean connectStarted;
        private volatile boolean loginSuccess;
        private volatile boolean loginFailed;
        private long connectStartTime;

        public StressClientInfo(String uid, SocketClient client) {
            this.uid = uid;
            this.client = client;
        }

        public boolean isConnected() {
            return client != null && client.isConnectSuccess();
        }
    }

    public static void initialize() {
        MessageUtil.init();
        keepaliveScheduler.scheduleAtFixedRate(
                StressManager::pulseKeepalivePing,
                KEEPALIVE_PING_INITIAL_SEC,
                KEEPALIVE_PING_INTERVAL_SEC,
                TimeUnit.SECONDS);
    }

    /** 对已登录压测客户端发送心跳 */
    private static void pulseKeepalivePing() {
        if (!running && clients.isEmpty()) {
            return;
        }
        for (StressClientInfo info : clients.values()) {
            if (!info.isLoginSuccess() || !info.isConnected()) {
                continue;
            }
            SocketClient client = info.getClient();
            if (client == null || !client.isActive()) {
                continue;
            }
            try {
                ByteString pingData = LoginProto.MC2S_ClientPing.newBuilder()
                        .setTime(System.currentTimeMillis())
                        .build()
                        .toByteString();
                client.sendMsg(TopicProto.TOPIC.TOPIC_TYPE_LOGIN,
                        LoginProto.FROM_CLIENT.C2S_ClientPing_VALUE, pingData);
            } catch (Exception e) {
                LogCore.Stress.error("keepalive ping failed, uid={}", info.getUid(), e);
            }
        }
    }

    public static boolean isStressClient(String uid) {
        return uid != null && uid.startsWith(uidPrefix) && clients.containsKey(uid);
    }

    private static boolean isStressUid(String uid) {
        return uid != null && uid.startsWith(uidPrefix);
    }

    private static void log(String message) {
        LogCore.Stress.info(message);
        if (logCallback != null) {
            logCallback.accept(message);
        }
    }

    public static void updateStats() {
        if (statsCallback == null) {
            return;
        }
        int total = clients.size();
        int addressFetched = 0;
        int loginSuccess = 0;
        int loginFailed = 0;
        int connected = 0;
        for (StressClientInfo info : clients.values()) {
            if (info.isAddressFetched()) {
                addressFetched++;
            }
            if (info.isLoginSuccess()) {
                loginSuccess++;
            }
            if (info.isLoginFailed()) {
                loginFailed++;
            }
            if (info.isConnected()) {
                connected++;
            }
        }
        statsCallback.accept(new Stats(total, addressFetched, loginSuccess, loginFailed, connected));
    }

    public static void addClients(int count) {
        if (count <= 0) {
            return;
        }
        running = true;
        currentBatchUids.clear();
        currentBatchSize = count;
        batchAddressDone.set(0);
        batchAddressSuccess.set(0);
        batchLoginDone.set(0);
        batchLoginSuccess.set(0);
        phase1StartTime = System.currentTimeMillis();

        log(String.format("[阶段1] 开始获取对外服地址，本批人数=%d", count));

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            if (!running) {
                break;
            }
            final int index = clientCounter.incrementAndGet();
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> runClientBatch(index), workerPool);
            futures.add(future);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenRun(StressManager::updateStats);
    }

    private static void runClientBatch(int index) {
        String uid = uidPrefix + index;
        if (clients.containsKey(uid)) {
            log(uid + " 已存在，跳过");
            onAddressPhaseComplete(uid, false, null);
            return;
        }

        SocketClient client = createClient();
        client.setUid(uid);

        StressClientInfo info = new StressClientInfo(uid, client);
        clients.put(uid, info);
        currentBatchUids.add(uid);
        SocketClientManager.addClient(client);

//        log(uid + ": 正在请求对外服地址...");
        try {
            LoginManager.ExternalAddress address = LoginManager.fetchExternalAddressOnly(uid).get();
            if (address == null) {
                log(uid + ": 获取对外服地址失败");
                info.setAddressFailed(true);
                onAddressPhaseComplete(uid, false, null);
                return;
            }
            info.setAddress(address);
            info.setAddressFetched(true);
            onAddressPhaseComplete(uid, true, address);
        } catch (Exception e) {
            LogCore.Stress.error("fetch address failed, uid={}", uid, e);
            info.setAddressFailed(true);
            onAddressPhaseComplete(uid, false, null);
        }
    }

    private static void onAddressPhaseComplete(String uid, boolean success, LoginManager.ExternalAddress address) {
        if (!currentBatchUids.contains(uid)) {
            return;
        }
        if (success) {
            batchAddressSuccess.incrementAndGet();
        }
        int done = batchAddressDone.incrementAndGet();
        if (done < currentBatchSize) {
            updateStats();
            return;
        }

        long elapsed = System.currentTimeMillis() - phase1StartTime;
        int successCount = batchAddressSuccess.get();
        log(String.format("[阶段1] 全部完成获取对外服地址: 成功=%d/%d, 总用时=%d ms",
                successCount, currentBatchSize, elapsed));
        updateStats();

        if (successCount > 0) {
            startConnectPhase();
        } else {
            log("[阶段2] 跳过：本批无人成功获取对外服地址");
        }
    }

    private static void startConnectPhase() {
        phase2StartTime = System.currentTimeMillis();
        int connectCount = 0;
        for (String uid : currentBatchUids) {
            StressClientInfo info = clients.get(uid);
            if (info == null || !info.isAddressFetched() || info.getAddress() == null || info.isConnectStarted()) {
                continue;
            }
            connectCount++;
        }
        if (connectCount == 0) {
            log("[阶段2] 无可用客户端可连接");
            return;
        }

        currentBatchLoginExpected = connectCount;
        batchLoginDone.set(0);
        batchLoginSuccess.set(0);

        log(String.format("[阶段2] 开始连接对外服并登录，人数=%d", connectCount));

        for (String uid : currentBatchUids) {
            StressClientInfo info = clients.get(uid);
            if (info == null || !info.isAddressFetched() || info.getAddress() == null || info.isConnectStarted()) {
                continue;
            }
            info.setConnectStarted(true);
            info.setConnectStartTime(System.currentTimeMillis());
            workerPool.execute(() -> connectClient(info));
        }
    }

    private static void connectClient(StressClientInfo info) {
        String uid = info.getUid();
        try {
            Boolean ok = LoginManager.connectAndSendLogin(info.getClient(), info.getAddress(), s -> {})
                    .get(20, TimeUnit.SECONDS);
            if (!Boolean.TRUE.equals(ok)) {
                info.setLoginFailed(true);
                onLoginPhaseComplete(uid, false);
            }
        } catch (Exception e) {
            LogCore.Stress.error("connect failed, uid={}", uid, e);
            info.setLoginFailed(true);
            onLoginPhaseComplete(uid, false);
        }
    }

    public static void onSelectHuman(SocketClient client) {
        if (client == null || client.getUid() == null) {
            return;
        }
        StressClientInfo info = clients.get(client.getUid());
        if (info == null || info.isLoginSuccess()) {
            return;
        }

        info.setLoginSuccess(true);

        onLoginPhaseComplete(info.getUid(), true);
        updateStats();
    }

    private static void onLoginPhaseComplete(String uid, boolean success) {
        if (!currentBatchUids.contains(uid)) {
            return;
        }
        if (success) {
            batchLoginSuccess.incrementAndGet();
        } else {
            StressClientInfo info = clients.get(uid);
            if (info != null && !info.isLoginSuccess()) {
                info.setLoginFailed(true);
            }
        }
        int done = batchLoginDone.incrementAndGet();
        if (done < currentBatchLoginExpected) {
            return;
        }
        long elapsed = System.currentTimeMillis() - phase2StartTime;
        int successCount = batchLoginSuccess.get();
        log(String.format("[阶段2] 本批登录阶段结束: 收到选角完成=%d/%d, 从连接对外服到全部选角完成总用时=%d ms",
                successCount, currentBatchLoginExpected, elapsed));
    }

    /**
     * 单连接发包状态：仅在对应 Netty EventLoop 上读写，无需原子类型
     */
    private static final class ClientPacketState {
        long remaining;
        int inflight;
        boolean clientSendCompleted;

        ClientPacketState(long remaining) {
            this.remaining = remaining;
        }
    }

    private static class PacketStressSession {
        private final PacketMode mode;
        private final long expectedTotal;
        private final int senderCount;
        private final byte[] payloadBytes;
        private final SocketMessage outboundMessage;
        private final LongAdder sentCount = new LongAdder();
        /** 全局已收包数，用于首/末包时间与结束判定，必须精确 */
        private final AtomicLong receivedCount = new AtomicLong(0);
        private final ConcurrentHashMap<String, ClientPacketState> clientStates = new ConcurrentHashMap<>();
        private final AtomicInteger sendersDone = new AtomicInteger(0);
        private volatile boolean active = true;
        private volatile boolean sendCompleted;
        private volatile long sendStartTime;
        private volatile long sendEndTime;
        private volatile long firstResponseTime;
        private volatile long lastResponseTime;
        private volatile ScheduledFuture<?> progressFuture;
        private volatile ScheduledFuture<?> sendProgressFuture;
        private volatile ScheduledFuture<?> stallDiagnosticFuture;
        private volatile boolean slowWaitProgressLogged;
        private volatile long lastProgressReceived;
        private volatile long lastProgressChangeTime;

        PacketStressSession(PacketMode mode, long expectedTotal, int senderCount, byte[] payloadBytes) {
            this.mode = mode;
            this.expectedTotal = expectedTotal;
            this.senderCount = senderCount;
            this.payloadBytes = payloadBytes;
            this.outboundMessage = new SocketMessage(MessageType.biz, payloadBytes);
        }

        boolean matchesResponse(PacketMode responseMode) {
            return mode == responseMode;
        }

        long sentTotal() {
            return sentCount.sum();
        }

        long inflightTotal() {
            long sum = 0;
            for (ClientPacketState state : clientStates.values()) {
                sum += state.inflight;
            }
            return sum;
        }
    }

    public static void startPacketStress(PacketMode mode, long totalPackets) {
        if (totalPackets <= 0) {
            log("[发包压测] 发包总数必须大于 0");
            return;
        }
        if (packetSession != null && packetSession.active) {
            log("[发包压测] 已有压测进行中，请等待完成或超时");
            return;
        }

        List<StressClientInfo> ready = new ArrayList<>();
        for (StressClientInfo info : clients.values()) {
            if (info.isLoginSuccess() && info.isConnected()) {
                ready.add(info);
            }
        }
        if (ready.isEmpty()) {
            log("[发包压测] 无已登录成功的客户端，请先完成登录");
            return;
        }

        int clientCount = ready.size();
        long perClient = totalPackets / clientCount;
        long remainder = totalPackets % clientCount;
        int activeSenders = 0;
        for (int c = 0; c < clientCount; c++) {
            if (perClient + (c < remainder ? 1 : 0) > 0) {
                activeSenders++;
            }
        }
        if (activeSenders == 0) {
            log("[发包压测] 无有效发包任务");
            return;
        }

        byte[] payloadBytes = buildStressPayloadBytes(mode);
        PacketStressSession session = new PacketStressSession(mode, totalPackets, activeSenders, payloadBytes);
        session.lastProgressChangeTime = System.currentTimeMillis();
        packetSession = session;

        String modeName = mode == PacketMode.PING ? "Ping包(S2C_ClientPing)" : "业务包(S2C_ItemList)";
        String responseName = mode == PacketMode.PING ? "S2C_ClientPing" : "S2C_ItemList";
        log(String.format("[发包压测] 开始: 模式=%s, 发包总数=%d, 在线人数=%d, 以收到%s回包统计TPS",
                modeName, totalPackets, ready.size(), responseName));

        timeoutScheduler.schedule(() -> onPacketStressTimeout(session),
                PACKET_STRESS_TIMEOUT_MINUTES, TimeUnit.MINUTES);

        startSendProgressLogging(session);
        runPacketStressSend(session, ready);
    }

    private static void startSendProgressLogging(PacketStressSession session) {
        cancelSendProgressLogging(session);
        session.lastProgressReceived = session.receivedCount.get();
        session.lastProgressChangeTime = System.currentTimeMillis();
        session.sendProgressFuture = timeoutScheduler.scheduleAtFixedRate(() -> {
            if (!session.active) {
                return;
            }
            long sent = session.sentTotal();
            long received = session.receivedCount.get();
            if (session.sendCompleted && received >= session.expectedTotal) {
                return;
            }
            long now = System.currentTimeMillis();
            if (received > session.lastProgressReceived) {
                session.lastProgressReceived = received;
                session.lastProgressChangeTime = now;
            } else if (session.sendCompleted
                    && received < session.expectedTotal
                    && now - session.lastProgressChangeTime >= PACKET_STALL_THRESHOLD_SEC * 1000L) {
                logPacketStallDiagnostics(session, "收包连续" + PACKET_STALL_THRESHOLD_SEC + "秒无进展");
                session.lastProgressChangeTime = now;
            }
            log(String.format(
                    "[发包压测] 进行中: 已发送=%d/%d, 已收到=%d/%d, 在途未回包=%d",
                    sent, session.expectedTotal, received, session.expectedTotal, session.inflightTotal()));
        }, PACKET_SEND_PROGRESS_SEC, PACKET_SEND_PROGRESS_SEC, TimeUnit.SECONDS);
    }

    private static void cancelSendProgressLogging(PacketStressSession session) {
        ScheduledFuture<?> future = session.sendProgressFuture;
        if (future != null) {
            future.cancel(false);
            session.sendProgressFuture = null;
        }
    }

    private static byte[] buildStressPayloadBytes(PacketMode mode) {
        return switch (mode) {
            case PING -> {
                ByteString pingData = LoginProto.MC2S_ClientPing.newBuilder()
                        .setTime(0L)
                        .build()
                        .toByteString();
                yield TopicProto.MBasePacketData.newBuilder()
                        .setPacketType(TopicProto.TOPIC.TOPIC_TYPE_LOGIN)
                        .setPacketId(LoginProto.FROM_CLIENT.C2S_ClientPing_VALUE)
                        .setPacketData(pingData)
                        .build()
                        .toByteArray();
            }
            case BUSINESS_GET_ITEM_LIST -> TopicProto.MBasePacketData.newBuilder()
                    .setPacketType(TopicProto.TOPIC.TOPIC_TYPE_ITEM)
                    .setPacketId(ItemProto.FROM_CLIENT.C2S_GetItemList_VALUE)
                    .build()
                    .toByteArray();
        };
    }

    /**
     * 压测收包快速路径：仅扫描 topic/packetId 字段，跳过 Router 与 packet_data 解析
     */
    public static boolean tryFastRouteStressResponse(SocketClient client, byte[] bytes) {
        PacketStressSession session = packetSession;
        String uid = client != null ? client.getUid() : null;
        if (session == null || !session.active || uid == null || !isStressUid(uid)) {
            return false;
        }
        if (session.clientStates.get(uid) == null) {
            return false;
        }
        int topicNum;
        int packetId;
        PacketMode mode;
        switch (session.mode) {
            case PING -> {
                topicNum = TopicProto.TOPIC.TOPIC_TYPE_LOGIN_VALUE;
                packetId = LoginProto.FROM_SERVER.S2C_ClientPing_VALUE;
                mode = PacketMode.PING;
            }
            case BUSINESS_GET_ITEM_LIST -> {
                topicNum = TopicProto.TOPIC.TOPIC_TYPE_ITEM_VALUE;
                packetId = ItemProto.FROM_SERVER.S2C_ItemList_VALUE;
                mode = PacketMode.BUSINESS_GET_ITEM_LIST;
            }
            default -> {
                return false;
            }
        }
        if (!matchesStressResponsePacket(bytes, topicNum, packetId)) {
            return false;
        }
        ClientPacketState state = session.clientStates.get(uid);
        if (state == null) {
            return false;
        }
        synchronized (state) {
            if (state.inflight <= 0) {
                return false;
            }
        }
        onPacketResponse(client, mode);
        return true;
    }

    private static boolean matchesStressResponsePacket(byte[] bytes, int expectedTopic, int expectedPacketId) {
        try {
            CodedInputStream in = CodedInputStream.newInstance(bytes, 0, bytes.length);
            int topic = -1;
            int packetId = -1;
            while (!in.isAtEnd()) {
                int tag = in.readTag();
                if (tag == 0) {
                    break;
                }
                switch (WireFormat.getTagFieldNumber(tag)) {
                    case 1 -> topic = in.readEnum();
                    case 2 -> packetId = in.readUInt32();
                    default -> in.skipField(tag);
                }
            }
            return topic == expectedTopic && packetId == expectedPacketId;
        } catch (Exception e) {
            return false;
        }
    }

    private static void runPacketStressSend(PacketStressSession session, List<StressClientInfo> ready) {
        session.sendStartTime = System.currentTimeMillis();
        int clientCount = ready.size();
        long perClient = session.expectedTotal / clientCount;
        long remainder = session.expectedTotal % clientCount;
        int activeSenders = 0;

        for (int c = 0; c < clientCount; c++) {
            long count = perClient + (c < remainder ? 1 : 0);
            if (count <= 0) {
                continue;
            }
            activeSenders++;
            StressClientInfo info = ready.get(c);
            String uid = info.getUid();
            session.clientStates.put(uid, new ClientPacketState(count));
            scheduleDrain(session, uid, info.getClient());
        }

        if (activeSenders == 0) {
            onPacketSendAllDone(session);
        }
    }

    private static void dispatchToClientThread(SocketClient client, Runnable task) {
        Channel channel = client.getChannel();
        if (channel != null && channel.eventLoop() != null) {
            channel.eventLoop().execute(task);
        } else {
            workerPool.execute(task);
        }
    }

    private static void drainSendQueue(PacketStressSession session, String uid) {
        if (!running || !session.active) {
            return;
        }
        ClientPacketState state = session.clientStates.get(uid);
        StressClientInfo info = clients.get(uid);
        if (state == null || info == null) {
            return;
        }

        SocketClient client = info.getClient();
        if (!info.isConnected() || client == null || !client.isActive()) {
            return;
        }

        if (!client.isChannelWritable()) {
            scheduleSendRetry(session, uid, client);
            return;
        }

        SocketMessage outbound = session.outboundMessage;
        int planned;

        synchronized (state) {
            planned = 0;
            while (session.active && state.remaining > 0 && state.inflight < MAX_INFLIGHT_PER_CLIENT) {
                planned++;
                state.remaining--;
                state.inflight++;
            }
        }

        if (planned == 0) {
            return;
        }

        int sent = 0;
        for (int i = 0; i < planned; i++) {
            if (!client.writeOutbound(outbound, false)) {
                break;
            }
            sent++;
        }

        if (sent > 0) {
            session.sentCount.add(sent);
            client.flushChannel();
        }

        if (sent < planned) {
            int rollback = planned - sent;
            synchronized (state) {
                state.remaining += rollback;
                state.inflight -= rollback;
            }
            if (sent == 0) {
                scheduleSendRetry(session, uid, client);
                return;
            }
        }

        boolean markClientDone = false;
        synchronized (state) {
            if (state.remaining <= 0 && !state.clientSendCompleted) {
                state.clientSendCompleted = true;
                markClientDone = true;
            }
        }
        if (markClientDone) {
            onClientSendDone(session);
            return;
        }

        if (!session.active) {
            return;
        }

        synchronized (state) {
            if (state.remaining > 0 && state.inflight < MAX_INFLIGHT_PER_CLIENT && !client.isChannelWritable()) {
                scheduleSendRetry(session, uid, client);
            }
        }
    }

    private static void scheduleDrain(PacketStressSession session, String uid, SocketClient client) {
        if (session.clientStates.get(uid) == null) {
            return;
        }
        dispatchToClientThread(client, () -> {
            drainSendQueue(session, uid);
            if (hasMoreToSend(session, uid)) {
                scheduleDrain(session, uid, client);
            }
        });
    }

    private static boolean hasMoreToSend(PacketStressSession session, String uid) {
        if (!running || !session.active) {
            return false;
        }
        ClientPacketState state = session.clientStates.get(uid);
        if (state == null) {
            return false;
        }
        synchronized (state) {
            return state.remaining > 0 && state.inflight < MAX_INFLIGHT_PER_CLIENT;
        }
    }

    private static void scheduleSendRetry(PacketStressSession session, String uid, SocketClient client) {
        Channel channel = client.getChannel();
        Runnable retry = () -> drainSendQueue(session, uid);
        if (channel != null && channel.eventLoop() != null) {
            channel.eventLoop().schedule(retry, 2, TimeUnit.MILLISECONDS);
        } else {
            timeoutScheduler.schedule(retry, 2, TimeUnit.MILLISECONDS);
        }
    }

    private static void onClientSendDone(PacketStressSession session) {
        if (session.sendersDone.incrementAndGet() == session.senderCount) {
            onPacketSendAllDone(session);
        }
    }

    private static void onPacketSendAllDone(PacketStressSession session) {
        if (session.sendCompleted) {
            tryCompletePacketStress(session);
            return;
        }
        session.sendCompleted = true;
        session.sendEndTime = System.currentTimeMillis();
        long sendElapsed = session.sendEndTime - session.sendStartTime;
        long inflight = session.inflightTotal();
        log(String.format(
                "[发包压测] 全部发送完毕: 已发送=%d/%d, 发送耗时=%d ms, 在途未回包=%d, 等待服务器回包...",
                session.sentTotal(), session.expectedTotal, sendElapsed, inflight));
        cancelSendProgressLogging(session);
        schedulePacketProgressIfSlow(session);
        startStallDiagnosticLogging(session);
        tryCompletePacketStress(session);
    }

    private static void startStallDiagnosticLogging(PacketStressSession session) {
        cancelStallDiagnosticLogging(session);
        session.stallDiagnosticFuture = timeoutScheduler.scheduleAtFixedRate(() -> {
            if (!session.active || !session.sendCompleted) {
                return;
            }
            if (session.receivedCount.get() >= session.expectedTotal) {
                return;
            }
            long waitMs = session.sendEndTime > 0
                    ? System.currentTimeMillis() - session.sendEndTime
                    : 0;
            if (waitMs < PACKET_STALL_THRESHOLD_SEC * 1000L) {
                return;
            }
            logPacketStallDiagnostics(session, "发送完毕已等待=" + waitMs + "ms仍无收齐");
        }, PACKET_STALL_DIAGNOSTIC_SEC, PACKET_STALL_DIAGNOSTIC_SEC, TimeUnit.SECONDS);
    }

    private static void cancelStallDiagnosticLogging(PacketStressSession session) {
        ScheduledFuture<?> future = session.stallDiagnosticFuture;
        if (future != null) {
            future.cancel(false);
            session.stallDiagnosticFuture = null;
        }
    }

    /**
     * 长时间无回包时输出客户端侧诊断，便于对照服务端 [Dispatch诊断]/[对外服诊断]/[游戏服诊断] 日志。
     */
    private static void logPacketStallDiagnostics(PacketStressSession session, String reason) {
        long sent = session.sentTotal();
        long received = session.receivedCount.get();
        long inflight = session.inflightTotal();

        int loginOk = 0;
        int connected = 0;
        int disconnected = 0;
        int notWritable = 0;
        int highInflightClients = 0;
        long maxInflight = 0;
        String maxInflightUid = "";

        for (Map.Entry<String, ClientPacketState> entry : session.clientStates.entrySet()) {
            String uid = entry.getKey();
            ClientPacketState state = entry.getValue();
            StressClientInfo info = clients.get(uid);
            if (info != null) {
                if (info.isLoginSuccess()) {
                    loginOk++;
                }
                if (info.isConnected()) {
                    connected++;
                } else {
                    disconnected++;
                }
                SocketClient client = info.getClient();
                if (client != null && client.isActive() && !client.isChannelWritable()) {
                    notWritable++;
                }
            }
            synchronized (state) {
                if (state.inflight > maxInflight) {
                    maxInflight = state.inflight;
                    maxInflightUid = uid;
                }
                if (state.inflight >= MAX_INFLIGHT_PER_CLIENT / 2) {
                    highInflightClients++;
                }
            }
        }

        log(String.format(
                "[发包压测][诊断] %s | 已发送=%d 已收到=%d/%d 在途=%d | 参与连接=%d 登录成功=%d 仍连接=%d 已断开=%d 通道不可写=%d",
                reason, sent, received, session.expectedTotal, inflight,
                session.clientStates.size(), loginOk, connected, disconnected, notWritable));
        log(String.format(
                "[发包压测][诊断] 在途偏高连接数(>=%d)=%d 最大在途=%d uid=%s | 请同时查服务端: RpcServer=[Dispatch诊断] ExternalServer=[对外服诊断] GameServer=[游戏服诊断]",
                MAX_INFLIGHT_PER_CLIENT / 2, highInflightClients, maxInflight, maxInflightUid));
        log(String.format(
                "[发包压测][诊断] 若 GameServer 出现 ping无humanId 或 对外服 待转发总数持续增长，多为未选角或 Dispatch 卡死；请 jstack RpcServerMessageManager 线程"));
    }

    private static void schedulePacketProgressIfSlow(PacketStressSession session) {
        cancelPacketProgressLogging(session);
        session.progressFuture = timeoutScheduler.schedule(() -> {
            if (!session.active || !session.sendCompleted || session.slowWaitProgressLogged) {
                return;
            }
            if (session.receivedCount.get() >= session.expectedTotal) {
                return;
            }
            session.slowWaitProgressLogged = true;
            long waitMs = session.sendEndTime > 0
                    ? System.currentTimeMillis() - session.sendEndTime
                    : 0;
            log(String.format(
                    "[发包压测] 仍在等待回包: 已收到=%d/%d, 在途未回包=%d, 已等待=%d ms",
                    session.receivedCount.get(), session.expectedTotal, session.inflightTotal(), waitMs));
        }, PACKET_PROGRESS_LOG_AFTER_MS, TimeUnit.MILLISECONDS);
    }

    private static void cancelPacketProgressLogging(PacketStressSession session) {
        ScheduledFuture<?> future = session.progressFuture;
        if (future != null) {
            future.cancel(false);
            session.progressFuture = null;
        }
    }

    private static void tryCompletePacketStress(PacketStressSession session) {
        if (!session.sendCompleted) {
            return;
        }
        if (session.receivedCount.get() < session.expectedTotal) {
            return;
        }
        finishPacketStress(session, false);
    }

    /**
     * 收到与当前压测模式匹配的服务器回包时调用
     */
    public static void onPacketResponse(SocketClient client, PacketMode responseMode) {
        if (client == null || client.getUid() == null) {
            return;
        }
        PacketStressSession session = packetSession;
        if (session == null || !session.active) {
            return;
        }
        if (!session.matchesResponse(responseMode)) {
            return;
        }

        String uid = client.getUid();
        ClientPacketState state = session.clientStates.get(uid);
        if (state == null) {
            return;
        }
        synchronized (state) {
            if (state.inflight <= 0) {
                return;
            }
            state.inflight--;
        }

        scheduleDrain(session, uid, client);

        long received = session.receivedCount.incrementAndGet();
        long now = System.currentTimeMillis();
        if (received == 1) {
            session.firstResponseTime = now;
        }
        session.lastResponseTime = now;

        tryCompletePacketStress(session);
    }

    private static void onPacketStressTimeout(PacketStressSession session) {
        if (session == null || !session.active) {
            return;
        }
        if (packetSession != session) {
            return;
        }
        finishPacketStress(session, true);
    }

    private static synchronized void finishPacketStress(PacketStressSession session, boolean timeout) {
        if (session == null || !session.active) {
            return;
        }
        session.active = false;
        cancelSendProgressLogging(session);
        cancelPacketProgressLogging(session);
        cancelStallDiagnosticLogging(session);
        if (packetSession == session) {
            packetSession = null;
        }

        long sent = session.sentTotal();
        long received = session.receivedCount.get();
        long sendElapsed = session.sendEndTime > 0
                ? session.sendEndTime - session.sendStartTime
                : System.currentTimeMillis() - session.sendStartTime;
        long pending = Math.max(0, sent - received);

        if (timeout) {
            log(String.format(
                    "[发包压测] 超时(%d分钟): 已发送=%d, 已收到回包=%d/%d, 在途未回包≈%d, 发送耗时=%d ms（可能因无流控打满链路/服务端队列导致丢包）",
                    PACKET_STRESS_TIMEOUT_MINUTES, sent, received, session.expectedTotal, pending, sendElapsed));
            logPacketStallDiagnostics(session, "压测超时");
            return;
        }

        long totalElapsed = session.lastResponseTime > 0
                ? session.lastResponseTime - session.sendStartTime
                : 0;
        long responseWindow = (session.firstResponseTime > 0 && session.lastResponseTime > 0)
                ? session.lastResponseTime - session.firstResponseTime
                : 0;
        double tpsTotal = totalElapsed > 0 ? received * 1000.0 / totalElapsed : 0;
        double tpsResponseWindow = responseWindow > 0 ? received * 1000.0 / responseWindow : 0;

        log(String.format(
                "[发包压测] 完成: 已发送=%d, 已收到回包=%d/%d, 发送耗时=%d ms, 首包回包至末包回包=%d ms, 总耗时(发起到收齐回包)=%d ms, TPS(全程)=%.2f, TPS(仅回包窗口)=%.2f",
                sent, received, session.expectedTotal, sendElapsed, responseWindow, totalElapsed, tpsTotal, tpsResponseWindow));
        if (sent != session.expectedTotal) {
            log(String.format("[发包压测] 警告: 实际发送(%d)与预期(%d)不一致，TPS 请以已收到回包为准",
                    sent, session.expectedTotal));
        }
        if (received > session.expectedTotal) {
            log(String.format("[发包压测] 警告: 收到回包数(%d)大于预期(%d)，请检查是否有重复计数",
                    received, session.expectedTotal));
        }
    }

    private static SocketClient createClient() {
        String socketType = ConfigReader.getProp().getProperty("client.socket", "tcp");
        return switch (socketType) {
            case "websocket" -> new WsClient();
            case "kcp" -> new KcpClientImpl();
            default -> new TcpClient();
        };
    }

    public static void removeClients(int count) {
        log("开始移除 " + count + " 个压测客户端...");
        List<String> toRemove = new ArrayList<>();
        Iterator<Map.Entry<String, StressClientInfo>> iterator = clients.entrySet().iterator();
        while (iterator.hasNext() && toRemove.size() < count) {
            toRemove.add(iterator.next().getKey());
        }
        for (String uid : toRemove) {
            removeClient(uid);
        }
        log("已移除 " + toRemove.size() + " 个压测客户端");
        updateStats();
    }

    public static void removeClient(String uid) {
        StressClientInfo info = clients.remove(uid);
        if (info != null) {
            SocketClient client = info.getClient();
            if (client != null && client.isActive()) {
                client.close();
            }
            SocketClientManager.removeClient(uid);
            log("已移除: " + uid);
        }
    }

    public static void stopAll() {
        running = false;
        if (packetSession != null) {
            packetSession.active = false;
            packetSession = null;
        }
        log("正在停止所有压测客户端...");
        for (StressClientInfo info : clients.values()) {
            SocketClient client = info.getClient();
            if (client != null && client.isActive()) {
                client.close();
            }
            SocketClientManager.removeClient(info.getUid());
        }
        clients.clear();
        clientCounter.set(0);
        currentBatchSize = 0;
        currentBatchUids.clear();
        log("所有压测客户端已停止");
        updateStats();
    }

    public static void shutdown() {
        if (packetSession != null) {
            packetSession.active = false;
            packetSession = null;
        }
        stopAll();
        workerPool.shutdown();
        timeoutScheduler.shutdown();
        keepaliveScheduler.shutdown();
    }
}
