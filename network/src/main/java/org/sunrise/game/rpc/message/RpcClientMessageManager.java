package org.sunrise.game.rpc.message;

import org.sunrise.game.core.message.ClientMessageManager;
import org.sunrise.game.core.message.MessageUtils;
import org.sunrise.game.log.LogCore;
import org.sunrise.game.rpc.function.Call;
import org.sunrise.game.rpc.function.CallType;
import org.sunrise.game.rpc.function.RpcFunction;
import org.sunrise.game.rpc.function.RpcManager;

import java.util.Map;

/**
 * pulseSender 向其他节点的server发送call
 * pulseHandler 处理rpc调用后的返回
 */
public class RpcClientMessageManager extends ClientMessageManager {
    public RpcClientMessageManager(String nodeId) {
        super(nodeId);
    }

    @Override
    public void pulse() {
        try {
            pulseHandler();
            pulseListenRpcTimeout();
            pulseSender();
        } catch (Exception e) {
            LogCore.RpcClient.error("DispatchThread pulse, error : ", e);
        }
    }

    @Override
    protected void pulseHandlerOne(Object data) {
        var message = MessageUtils.fromMessage((byte[]) data, Call.class);
        if (message.getType() == CallType.Call.ordinal()) {
            RpcManager.callResult(message);
        } else if (message.getType() == CallType.Update.ordinal()) {
            RpcFunction.onUpdate(message);
        }
    }

    /**
     * rpc超时检测
     */
    private void pulseListenRpcTimeout() {
        long cur = System.currentTimeMillis();
        for (Map.Entry<Long, Long> entry : RpcManager.checkTimeout.entrySet()) {
            if (cur > entry.getValue()) {
                RpcManager.checkTimeout.remove(entry.getKey());
                RpcManager.callTimeOut(entry.getKey());
            }
        }
    }
}
