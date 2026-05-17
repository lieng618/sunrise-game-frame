package core.client;

import com.google.protobuf.ByteString;
import core.message.MessageUtil;
import lombok.Getter;
import lombok.Setter;
import org.sunrise.game.config.ConfigReader;
import org.sunrise.game.genProto.gen.LoginProto;
import org.sunrise.game.genProto.gen.TopicProto;
import org.sunrise.game.log.LogCore;
import org.sunrise.game.utils.Utils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * 机器人管理器 - 管理所有压测机器人的生命周期
 */
public class BotManager {

    private static final ConcurrentHashMap<String, BotInfo> bots = new ConcurrentHashMap<>();
    private static final AtomicInteger botCounter = new AtomicInteger(0);
    private static final ScheduledExecutorService pingScheduler = Executors.newScheduledThreadPool(1);
    private static final ExecutorService loginExecutor = Executors.newFixedThreadPool(10);
    private static final String uidPrefix = "client";
    @Setter
    private static Consumer<String> logCallback;
    @Setter
    private static Consumer<Stats> statsCallback;
    private static volatile boolean running = false;

    @Getter
    public static class Stats {
        private final int total;
        private final int connected;
        private final int loginSuccess;
        private final int loginFailed;
        private final int disconnected;

        public Stats(int total, int connected, int loginSuccess, int loginFailed, int disconnected) {
            this.total = total;
            this.connected = connected;
            this.loginSuccess = loginSuccess;
            this.loginFailed = loginFailed;
            this.disconnected = disconnected;
        }
    }

    @Getter
    @Setter
    public static class BotInfo {
        private final String uid;
        private final SocketClient client;
        private volatile boolean loginSuccess = false;
        private volatile boolean loginFailed = false;
        private long loginStartTime;

        public BotInfo(String uid, SocketClient client) {
            this.uid = uid;
            this.client = client;
        }

        public boolean isConnected() {
            return client != null && client.isConnectSuccess();
        }
    }

    public static void initialize() {
        MessageUtil.init();

        pingScheduler.scheduleAtFixedRate(() -> {
            for (BotInfo bot : bots.values()) {
                if (bot.isConnected() && bot.isLoginSuccess()) {
                    try {
                        ByteString pingData = LoginProto.MC2S_ClientPing.newBuilder()
                                .setTime(System.currentTimeMillis())
                                .build()
                                .toByteString();
                        bot.getClient().sendMsg(TopicProto.TOPIC.TOPIC_TYPE_LOGIN,
                                LoginProto.FROM_CLIENT.C2S_ClientPing_VALUE, pingData);
                    } catch (Exception e) {
                        LogCore.Bot.error("Ping failed for bot: {}", bot.getUid(), e);
                    }
                }
            }
        }, 5, 10, TimeUnit.SECONDS);
    }

    private static void log(String message) {
        LogCore.Bot.info(message);
        if (logCallback != null) {
            logCallback.accept(message);
        }
    }

    public static void updateStats() {
        if (statsCallback != null) {
            int total = bots.size();
            int connected = 0;
            int loginSuccess = 0;
            int loginFailed = 0;
            for (BotInfo bot : bots.values()) {
                if (bot.isConnected()) {
                    connected++;
                }
                if (bot.isLoginSuccess()) {
                    loginSuccess++;
                }
                if (bot.isLoginFailed()) {
                    loginFailed++;
                }
            }
            int disconnected = total - connected;
            statsCallback.accept(new Stats(total, connected, loginSuccess, loginFailed, disconnected));
        }
    }

    public static void addBots(int count) {
        running = true;
        log("开始添加 " + count + " 个机器人...");

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            if (!running) {
                log("添加机器人已停止");
                break;
            }

            final int index = botCounter.incrementAndGet();
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                String uid = uidPrefix + index;
                if (bots.containsKey(uid)) {
                    log(uid + " 已存在，跳过");
                    return;
                }

                loginBot(uid);
            }, loginExecutor);
            futures.add(future);

            Utils.sleep(30);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenRun(() -> {
                    log("所有机器人添加完成");
                    updateStats();
                });
    }

    private static void loginBot(String uid) {
        SocketClient client = null;
        try {
            String socketType = ConfigReader.getProp().getProperty("client.socket", "tcp");
            switch (socketType) {
                case "websocket" -> client = new WsClient();
                case "kcp" -> client = new KcpClientImpl();
                default -> client = new TcpClient();
            }
            client.setUid(uid);

            BotInfo botInfo = new BotInfo(uid, client);
            botInfo.setLoginStartTime(System.currentTimeMillis());
            bots.put(uid, botInfo);

            log("正在登录机器人: " + uid);
            SocketClientManager.addClient(client);
            LoginManager.login(client, status -> {
                log(uid + ": " + status);
            });

            botInfo.setLoginSuccess(true);
            log("机器人登录成功: " + uid);
            updateStats();

        } catch (Exception e) {
            LogCore.Bot.error("Login bot failed: {}", uid, e);
            BotInfo bot = bots.get(uid);
            if (bot != null) {
                bot.setLoginFailed(true);
            }
            if (client != null) {
                SocketClientManager.removeClient(uid);
            }
            updateStats();
        }
    }

    public static void removeBots(int count) {
        log("开始移除 " + count + " 个机器人...");

        List<String> toRemove = new ArrayList<>();
        Iterator<Map.Entry<String, BotInfo>> iterator = bots.entrySet().iterator();
        while (iterator.hasNext() && toRemove.size() < count) {
            Map.Entry<String, BotInfo> entry = iterator.next();
            toRemove.add(entry.getKey());
        }

        for (String uid : toRemove) {
            removeBot(uid);
        }

        log("已移除 " + toRemove.size() + " 个机器人");
        updateStats();
    }

    public static void removeBot(String uid) {
        BotInfo bot = bots.remove(uid);
        if (bot != null) {
            SocketClient client = bot.getClient();
            if (client != null && client.isActive()) {
                client.close();
            }
            SocketClientManager.removeClient(uid);
            log("已移除机器人: " + uid);
        }
    }

    public static void stopAll() {
        running = false;
        log("正在停止所有机器人...");

        for (BotInfo bot : bots.values()) {
            SocketClient client = bot.getClient();
            if (client != null && client.isActive()) {
                client.close();
            }
            SocketClientManager.removeClient(bot.getUid());
        }
        bots.clear();
        botCounter.set(0);
        log("所有机器人已停止");
        updateStats();
    }

    public static void sendToAllBots(int topicType, int packetId, ByteString data, int interval, int times) {
        log("开始向所有在线机器人发送消息, 间隔=" + interval + "ms, 次数=" + times);

        for (BotInfo bot : bots.values()) {
            if (bot.isConnected() && bot.isLoginSuccess()) {
                CompletableFuture.runAsync(() -> {
                    SocketClient client = bot.getClient();
                    for (int i = 0; i < times; i++) {
                        try {
                            client.sendMsg(topicType, packetId, data);
                            if (i < times - 1 && interval > 0) {
                                Utils.sleep(interval);
                            }
                        } catch (Exception e) {
                            LogCore.Bot.error("Send message failed for bot: {}", bot.getUid(), e);
                        }
                    }
                    log(bot.getUid() + " 发送完成" + "topicType=" + topicType + " packetId=" + packetId);
                });
            }
        }
    }

    public static void shutdown() {
        stopAll();
        pingScheduler.shutdown();
        loginExecutor.shutdown();
    }
}
