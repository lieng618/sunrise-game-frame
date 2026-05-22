package org.sunrise.game.game.logic.system;

import org.sunrise.game.config.ConfigReader;
import org.sunrise.game.game.annotation.GameSystem;
import org.sunrise.game.game.human.ConnectObject;
import org.sunrise.game.game.login.LoginMsgHandler;
import org.sunrise.game.genProto.gen.LoginProto;
import org.sunrise.game.genProto.gen.TopicProto;
import org.sunrise.game.log.LogCore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

@GameSystem
public class LoginQueueSystem extends BaseSystem {
    private static final int DEFAULT_MAX_LOGIN_PER_SECOND = 100;
    private static final long TIMEOUT_MS = 30 * 60 * 1000L;

    private int maxLoginPerSecond = DEFAULT_MAX_LOGIN_PER_SECOND;

    private int currentSecondLoginCount = 0;
    private long currentSecond = 0;

    private final LinkedList<QueueEntry> queue = new LinkedList<>();
    private final HashMap<Long, QueueEntry> queueMap = new HashMap<>();
    private final HashMap<Long, String> connectExternalNodeId = new HashMap<>();

    public static class QueueEntry {
        public final long connectId;
        public final String uid;
        public long enqueueTime;

        QueueEntry(long connectId, String uid, long enqueueTime) {
            this.connectId = connectId;
            this.uid = uid;
            this.enqueueTime = enqueueTime;
        }
    }

    private static class PulseResult {
        final List<QueueEntry> dequeued = new ArrayList<>();
        final List<QueueEntry> timedOut = new ArrayList<>();
    }

    @Override
    public void init() {
        Properties properties = ConfigReader.getProp();
        int max = DEFAULT_MAX_LOGIN_PER_SECOND;
        if (properties != null) {
            String value = properties.getProperty("login.queue.maxPerSecond");
            if (value != null && !value.isBlank()) {
                try {
                    max = Integer.parseInt(value.trim());
                } catch (NumberFormatException e) {
                    LogCore.GameServer.warn("invalid login.queue.maxPerSecond={}, use default {}", value, DEFAULT_MAX_LOGIN_PER_SECOND);
                }
            }
        }
        if (max <= 0) {
            LogCore.GameServer.warn("login.queue.maxPerSecond must be > 0, use default {}", DEFAULT_MAX_LOGIN_PER_SECOND);
            max = DEFAULT_MAX_LOGIN_PER_SECOND;
        }
        maxLoginPerSecond = max;
        LogCore.GameServer.info("LoginQueueSystem init, login.queue.maxPerSecond = {}", maxLoginPerSecond);
    }

    /**
     * @return true 表示需要排队；false 表示本秒可直接登录
     */
    public boolean tryEnterOrQueue(long connectId, String uid) {
        long now = System.currentTimeMillis();
        checkSecondReset(now);

        QueueEntry existing = queueMap.get(connectId);
        if (existing != null) {
            existing.enqueueTime = now;
            return true;
        }

        if (queue.isEmpty() && currentSecondLoginCount < maxLoginPerSecond) {
            currentSecondLoginCount++;
            return false;
        }

        QueueEntry entry = new QueueEntry(connectId, uid, now);
        queue.addLast(entry);
        queueMap.put(connectId, entry);
        LogCore.GameServer.info("player queued, uid = {}, connectId = {}, queueSize = {}", uid, connectId, queue.size());
        return true;
    }

    /**
     * 当传入的对外服节点有效时，进行绑定
     */
    public void saveExternalNodeIdIfPresent(long connectId, String externalNodeId) {
        if (externalNodeId != null && !externalNodeId.isEmpty()) {
            connectExternalNodeId.put(connectId, externalNodeId);
        }
    }

    public String getExternalNodeId(long connectId) {
        return connectExternalNodeId.getOrDefault(connectId, "");
    }

    public void releaseConnect(long connectId) {
        connectExternalNodeId.remove(connectId);
    }

    public void sendQueueInfo(long connectId) {
        QueueEntry entry = queueMap.get(connectId);
        if (entry == null) {
            return;
        }
        int pos = getPosition(connectId);
        if (pos <= 0) {
            return;
        }
        ConnectObject.sendToClient(connectId, getExternalNodeId(connectId),
                TopicProto.TOPIC.TOPIC_TYPE_LOGIN_VALUE, LoginProto.FROM_SERVER.S2C_Queue_VALUE,
                LoginProto.MS2C_Queue.newBuilder().setPos(pos).setQueues(getQueueSize()).setNeedTime(getEstimatedWaitSeconds(pos)));
    }

    public int getPosition(long connectId) {
        int pos = 0;
        for (QueueEntry entry : queue) {
            pos++;
            if (entry.connectId == connectId) {
                return pos;
            }
        }
        return 0;
    }

    public int getQueueSize() {
        return queue.size();
    }

    public int getEstimatedWaitSeconds(int pos) {
        if (pos <= 0) {
            return 0;
        }
        int remainingThisSecond = Math.max(0, maxLoginPerSecond - currentSecondLoginCount);
        if (pos <= remainingThisSecond) {
            return 0;
        }
        return (int) Math.ceil((pos - remainingThisSecond) / (double) maxLoginPerSecond);
    }

    public void removeFromQueue(long connectId) {
        QueueEntry entry = queueMap.remove(connectId);
        if (entry != null) {
            queue.remove(entry);
        }
    }

    @Override
    public void pulsePerSec() {
        PulseResult result = pulseQueue();

        for (QueueEntry entry : result.timedOut) {
            ConnectObject.sendToClient(entry.connectId, getExternalNodeId(entry.connectId),
                    TopicProto.TOPIC.TOPIC_TYPE_LOGIN_VALUE, LoginProto.FROM_SERVER.S2C_Kick_VALUE,
                    LoginProto.MS2C_Kick.newBuilder().setReason("queue timeout"));
            releaseConnect(entry.connectId);
        }
        for (QueueEntry entry : result.dequeued) {
            LoginMsgHandler.processLogin(entry.connectId, entry.uid);
        }
    }

    private PulseResult pulseQueue() {
        long now = System.currentTimeMillis();
        PulseResult result = new PulseResult();

        Iterator<QueueEntry> it = queue.iterator();
        while (it.hasNext()) {
            QueueEntry entry = it.next();
            if (now - entry.enqueueTime > TIMEOUT_MS) {
                it.remove();
                queueMap.remove(entry.connectId);
                result.timedOut.add(entry);
                LogCore.GameServer.info("queue entry timed out, uid = {}, connectId = {}", entry.uid, entry.connectId);
            }
        }

        checkSecondReset(now);
        while (!queue.isEmpty() && currentSecondLoginCount < maxLoginPerSecond) {
            QueueEntry entry = queue.pollFirst();
            queueMap.remove(entry.connectId);
            currentSecondLoginCount++;
            result.dequeued.add(entry);
            LogCore.GameServer.info("player dequeued, uid = {}, connectId = {}, queueSize = {}", entry.uid, entry.connectId, queue.size());
        }

        return result;
    }

    private void checkSecondReset(long now) {
        long sec = now / 1000;
        if (sec != currentSecond) {
            currentSecond = sec;
            currentSecondLoginCount = 0;
        }
    }
}
