package org.sunrise.game.external.server;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import kcp.Ukcp;
import lombok.Data;
import org.sunrise.game.config.ConfigReader;
import org.sunrise.game.core.message.MessageType;
import org.sunrise.game.core.message.SocketMessage;
import org.sunrise.game.core.rate.TokenBucketRateLimiter;
import org.sunrise.game.log.LogCore;
import org.sunrise.game.utils.Utils;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.LongAdder;

@Data
public class ClientConnection {
    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final int DEFAULT_RATE_LIMIT_PER_MINUTE = 1200;
    private static final int DEFAULT_RATE_LIMIT_BURST = 120;
    private static final int DEFAULT_ABUSE_WINDOW_SECONDS = 5;
    private static final int DEFAULT_ABUSE_REJECT_THRESHOLD = 600;
    private static final int DEFAULT_REJECT_LOG_INTERVAL_MILLIS = 1000;

    private static final LongAdder ACCEPTED_MESSAGE_TOTAL = new LongAdder();
    private static final LongAdder RATE_LIMITED_MESSAGE_TOTAL = new LongAdder();
    private static final LongAdder RATE_LIMIT_CLOSED_CONNECTION_TOTAL = new LongAdder();

    private static int rateLimitPerMinute = -1;
    private static int rateLimitBurst = -1;
    private static int abuseWindowSeconds = -1;
    private static int abuseRejectThreshold = -1;
    private static int rejectLogIntervalMillis = -1;

    private long id;// 连接id
    private Channel channel;
    private Ukcp ukcp;
    private final TokenBucketRateLimiter inboundRateLimiter =
            new TokenBucketRateLimiter(rateLimitBurst(), rateLimitPerMinute());
    private long violationWindowStartNanos;
    private int rejectedInViolationWindow;
    private long lastRateLimitLogNanos;
    private boolean rateLimitClosed;
    private final Queue<byte[]> msgQueue = new ConcurrentLinkedQueue<>();//待转发到游戏服的消息队列
    private boolean firstSend; // 是否为首次发消息
    private String gameNodeId; //记录此玩家当前在哪个game服

    private static int rateLimitPerMinute() {
        if (rateLimitPerMinute < 0) {
            rateLimitPerMinute = positiveConfig(
                    "external.rate-limit.refill-per-minute", DEFAULT_RATE_LIMIT_PER_MINUTE);
        }
        return rateLimitPerMinute;
    }

    private static int rateLimitBurst() {
        if (rateLimitBurst < 0) {
            rateLimitBurst = positiveConfig(
                    "external.rate-limit.burst", DEFAULT_RATE_LIMIT_BURST);
        }
        return rateLimitBurst;
    }

    private static int abuseWindowSeconds() {
        if (abuseWindowSeconds < 0) {
            abuseWindowSeconds = positiveConfig(
                    "external.rate-limit.abuse-window-seconds", DEFAULT_ABUSE_WINDOW_SECONDS);
        }
        return abuseWindowSeconds;
    }

    private static int abuseRejectThreshold() {
        if (abuseRejectThreshold < 0) {
            abuseRejectThreshold = positiveConfig(
                    "external.rate-limit.abuse-threshold", DEFAULT_ABUSE_REJECT_THRESHOLD);
        }
        return abuseRejectThreshold;
    }

    private static int rejectLogIntervalMillis() {
        if (rejectLogIntervalMillis < 0) {
            rejectLogIntervalMillis = positiveConfig(
                    "external.rate-limit.log-interval-ms", DEFAULT_REJECT_LOG_INTERVAL_MILLIS);
        }
        return rejectLogIntervalMillis;
    }

    private static int positiveConfig(String key, int defaultValue) {
        return Math.max(1, ConfigReader.getInt(key, defaultValue));
    }

    public ClientConnection(Channel channel) {
        this.channel = channel;
    }

    public ClientConnection(Ukcp ukcp) {
        this.ukcp = ukcp;
    }

    public synchronized boolean dataCheck(byte[] data) {
        if (data.length > Utils.MSG_BYTE_LEN_MAX) {
            LogCore.BaseServer.error("recv from client, msg too large, connectionId = {}, size = {}, max = {}",
                    id, data.length, Utils.MSG_BYTE_LEN_MAX);
            return false;
        }

        if (rateLimitClosed) {
            return false;
        }

        long nowNanos = System.nanoTime();
        if (!inboundRateLimiter.tryAcquire(nowNanos)) {
            handleRateLimitRejection(nowNanos);
            return false;
        }

        ACCEPTED_MESSAGE_TOTAL.increment();
        return true;
    }

    private void handleRateLimitRejection(long nowNanos) {
        RATE_LIMITED_MESSAGE_TOTAL.increment();

        long abuseWindowNanos = abuseWindowSeconds() * NANOS_PER_SECOND;
        if (rejectedInViolationWindow == 0
                || nowNanos - violationWindowStartNanos >= abuseWindowNanos) {
            violationWindowStartNanos = nowNanos;
            rejectedInViolationWindow = 0;
        }

        rejectedInViolationWindow++;
        if (rejectedInViolationWindow >= abuseRejectThreshold()) {
            rateLimitClosed = true;
            RATE_LIMIT_CLOSED_CONNECTION_TOTAL.increment();
            LogCore.ExternalServer.error(
                    "closing abusive client, connectionId = {}, remoteAddress = {}, rejectedInWindow = {}, "
                            + "window = {}s, acceptedTotal = {}, rejectedTotal = {}",
                    id, getRemoteAddress(), rejectedInViolationWindow, abuseWindowSeconds(),
                    ACCEPTED_MESSAGE_TOTAL.sum(), RATE_LIMITED_MESSAGE_TOTAL.sum());
            close();
            return;
        }

        long logIntervalNanos = rejectLogIntervalMillis() * 1_000_000L;
        if (lastRateLimitLogNanos == 0L
                || nowNanos - lastRateLimitLogNanos >= logIntervalNanos
                || nowNanos < lastRateLimitLogNanos) {
            lastRateLimitLogNanos = nowNanos;
            LogCore.ExternalServer.warn(
                    "client message rate limited, connectionId = {}, remoteAddress = {}, "
                            + "refillPerMinute = {}, burst = {}, rejectedInWindow = {}, "
                            + "acceptedTotal = {}, rejectedTotal = {}",
                    id, getRemoteAddress(), rateLimitPerMinute(), rateLimitBurst(),
                    rejectedInViolationWindow, ACCEPTED_MESSAGE_TOTAL.sum(),
                    RATE_LIMITED_MESSAGE_TOTAL.sum());
        }
    }

    public void sendMessage(byte[] data) {
        if (channel != null && channel.isActive()) {
            channel.writeAndFlush(new SocketMessage(MessageType.biz, data));
        } else if (ukcp != null && ukcp.isActive()) {
            ByteBuf buf = Unpooled.buffer(4 + 4 + data.length);
            buf.writeInt(MessageType.biz);
            buf.writeInt(data.length);
            buf.writeBytes(data);
            ukcp.write(buf);
            buf.release();
        }
    }

    public void close() {
        if (channel != null) {
            channel.close();
        } else if (ukcp != null) {
            ukcp.close();
        }
    }

    public boolean isActive() {
        if (channel != null) {
            return channel.isActive();
        } else if (ukcp != null) {
            return ukcp.isActive();
        }
        return false;
    }

    public String getRemoteAddress() {
        if (channel != null && channel.remoteAddress() != null) {
            return channel.remoteAddress().toString();
        } else if (ukcp != null && ukcp.user() != null && ukcp.user().getRemoteAddress() != null) {
            return ukcp.user().getRemoteAddress().toString();
        }
        return "unknown";
    }

    public static long getAcceptedMessageTotal() {
        return ACCEPTED_MESSAGE_TOTAL.sum();
    }

    public static long getRateLimitedMessageTotal() {
        return RATE_LIMITED_MESSAGE_TOTAL.sum();
    }

    public static long getRateLimitClosedConnectionTotal() {
        return RATE_LIMIT_CLOSED_CONNECTION_TOTAL.sum();
    }
}
