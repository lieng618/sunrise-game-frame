package org.sunrise.game.rpc.function;

import org.sunrise.game.graceful.OnShutdown;
import org.sunrise.game.log.LogCore;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RpcManager {

    public static final long DEFAULT_TIMEOUT_MS = 10_000;

    public static final Map<Long, CallResult> callResults = new ConcurrentHashMap<>();
    public static final Map<Long, Long> checkTimeout = new ConcurrentHashMap<>();

    /**
     * rpc返回后，调用回调函数
     */
    public static void callResult(Call call) {
        CallResult callResult = callResults.remove(call.getMessageId());
        if (callResult != null) {
            LogCore.RpcServer.debug("rpc result, callId = {}, messageId = { {} }, result = {}, data = {}", call.getRpcId(), call.getMessageId(), call.getResult(), call.getData());

            checkTimeout.remove(call.getMessageId());
            callResult.getRpcResult().setData(call.getData());
            callResult.getRpcResult().setResult(call.getResult());
            callResult.getCallback().process(callResult.getRpcResult());
        }
    }

    /**
     * rpc超时后，调用回调函数
     */
    public static void callTimeOut(long uid) {
        CallResult callResult = callResults.remove(uid);
        if (callResult != null) {
            callResult.getRpcResult().setResult(ErrorType.RPC_TIMEOUT);
            callResult.getCallback().process(callResult.getRpcResult());
        }
    }

    public static void registerCallback(long uid, CallResult result) {
        callResults.put(uid, result);
        checkTimeout.put(uid, System.currentTimeMillis() + DEFAULT_TIMEOUT_MS);
    }

    /**
     * 手动设置超时时间，millis 为 0 时使用 {@link #DEFAULT_TIMEOUT_MS}
     */
    public static void setTimeOut(long uid, long millis) {
        CallResult callResult = callResults.get(uid);
        if (callResult != null) {
            long timeout = millis <= 0 ? DEFAULT_TIMEOUT_MS : millis;
            checkTimeout.put(uid, System.currentTimeMillis() + timeout);
        }
    }

    /**
     * 当前 pending 的 RPC 调用数
     */
    public static int getPendingCount() {
        return callResults.size();
    }

    /**
     * 停机时等待所有 pending RPC 调用完成或超时。
     *
     * @param timeoutMs 最大等待毫秒数
     */
    public static void awaitPending(long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        int pending;
        while ((pending = callResults.size()) > 0 && System.currentTimeMillis() < deadline) {
            LogCore.RpcServer.info("awaitPending: {} pending RPC calls remaining", pending);
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        int remaining = callResults.size();
        if (remaining > 0) {
            LogCore.RpcServer.warn("awaitPending timeout: {} RPC calls still pending after {}ms", remaining, timeoutMs);
        } else {
            LogCore.RpcServer.info("awaitPending: all RPC calls completed");
        }
    }

    /**
     * 停机时强制清理所有残留的 RPC pending 条目，触发 timeout 回调。
     * 在 awaitPending 超时后调用，确保所有回调都被触发（以 RPC_TIMEOUT 状态）。
     */
    public static void shutdown() {
        int cleaned = 0;
        Iterator<Map.Entry<Long, CallResult>> it = callResults.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, CallResult> entry = it.next();
            it.remove();
            checkTimeout.remove(entry.getKey());
            CallResult callResult = entry.getValue();
            if (callResult != null && callResult.getCallback() != null) {
                callResult.getRpcResult().setResult(ErrorType.RPC_SHUTDOWN);
                callResult.getCallback().process(callResult.getRpcResult());
                cleaned++;
            }
        }
        LogCore.RpcServer.info("RpcManager shutdown: cleaned {} pending calls", cleaned);
    }

    /**
     * 优雅停机入口：等待 pending RPC 完成 → 强制清理超时回调。
     */
    @OnShutdown(order = 20)
    public static void shutdownGracefully() {
        awaitPending(8000);
        shutdown();
    }
}
