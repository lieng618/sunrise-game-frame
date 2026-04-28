package org.sunrise.game.rpc.function;

import org.sunrise.game.log.LogCore;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RpcManager {

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
        checkTimeout.put(uid, System.currentTimeMillis() + 10 * 1000);
    }

    /**
     * 手动设置超时时间
     */
    public static void setTimeOut(long uid, long millis) {
        CallResult callResult = callResults.get(uid);
        if (callResult != null) {
            if (millis == 0) {
                checkTimeout.remove(uid);
            } else {
                checkTimeout.put(uid, System.currentTimeMillis() + millis);
            }
        }
    }
}
