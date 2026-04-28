package org.sunrise.game.rpc.message;

import org.sunrise.game.core.message.MessageUtils;
import org.sunrise.game.core.message.ServerMessageManager;
import org.sunrise.game.log.LogCore;
import org.sunrise.game.rpc.function.Call;
import org.sunrise.game.rpc.function.CallType;
import org.sunrise.game.rpc.function.CallUtils;
import org.sunrise.game.rpc.function.RpcFunction;
import org.sunrise.game.rpc.function.RpcManager;
import org.sunrise.game.rpc.service.ServiceManager;

import java.util.Map;

/**
 * pulseSender 将rpc处理后的返回值发给其他节点的client
 * pulseHandlerOne 处理其他节点的client发来的call，也会处理自身进程发来的call（RpcFunction 112行）
 * 同时rpc监听返回后，回调函数也会在pulseHandlerOne中执行(RpcNode 176行)
 */
public class RpcServerMessageManager extends ServerMessageManager {
    public RpcServerMessageManager(String nodeId) {
        super(nodeId);
    }

    @Override
    public void pulse() {
        try {
            pulseHandler();
            pulseSender();
            pulseListenRpcTimeout();
            ServiceManager.pulse();
        } catch (Exception e) {
            LogCore.RpcServer.error("DispatchThread pulse, error : ", e);
        }
    }

    @Override
    protected void pulseHandlerOne(Object data) {
        Call message = null;
        if (data instanceof byte[]) {
            message = MessageUtils.fromMessage((byte[]) data, Call.class);
        } else if (data instanceof Call) {
            message = (Call) data;
        }
        if (message == null) {
            return;
        }
        if (message.getType() == CallType.Call.ordinal()) {
            CallUtils.handler(message);
        } else if (message.getType() == CallType.CallResult.ordinal()) {
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
