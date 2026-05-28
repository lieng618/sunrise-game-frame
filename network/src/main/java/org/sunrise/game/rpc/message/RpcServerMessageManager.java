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

import java.util.Iterator;
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

    /**
     * 线程心跳
     */
    @Override
    public void pulse() {
        long pulseStart = System.currentTimeMillis();
        int rpcRecvBefore = getRecvMsgQueue().size();
        long handlerMs = 0L;
        long senderMs = 0L;
        int handlerProcessed = 0;
        int senderProcessed = 0;
        int rpcRecvAfter = 0;
        try {
            long phaseStart = System.currentTimeMillis();
            handlerProcessed = pulseHandler();
            handlerMs = System.currentTimeMillis() - phaseStart;

            phaseStart = System.currentTimeMillis();
            senderProcessed = pulseSender();
            senderMs = System.currentTimeMillis() - phaseStart;

            pulseListenRpcTimeout();
            ServiceManager.pulse();

            rpcRecvAfter = getRecvMsgQueue().size();
        } catch (Exception e) {
            LogCore.RpcServer.error("DispatchThread pulse, error : ", e);
        } finally {
            long totalMs = System.currentTimeMillis() - pulseStart;
            if (totalMs >= 100) {
                LogCore.RpcServer.warn(
                        "本次pulse 总耗时 {} ms, 消息处理耗时 {} ms, 消息发送耗时 {} ms, 消息接收队列起始总数 {}, 本次处理消息数 {}, 本次发送到远端的消息数 {}, 心跳结束时消息接收队列总数 {}",
                        totalMs, handlerMs, senderMs, rpcRecvBefore, handlerProcessed, senderProcessed, rpcRecvAfter);
            }
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
        Iterator<Map.Entry<Long, Long>> iterator = RpcManager.checkTimeout.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<Long, Long> entry = iterator.next();
            if (cur > entry.getValue()) {
                iterator.remove();
                RpcManager.callTimeOut(entry.getKey());
            }
        }
    }
}
