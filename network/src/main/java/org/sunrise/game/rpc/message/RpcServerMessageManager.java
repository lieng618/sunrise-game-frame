package org.sunrise.game.rpc.message;

import org.sunrise.game.core.message.MessageUtils;
import org.sunrise.game.core.message.ServerMessageManager;
import org.sunrise.game.log.LogCore;
import org.sunrise.game.rpc.function.Call;
import org.sunrise.game.rpc.function.CallUtils;
import org.sunrise.game.rpc.service.ServiceManager;

/**
 * pulseSender 将rpc处理后的返回值发给其他节点的client
 * pulseHandlerOne 处理其他节点的client发来的call，也会处理自身进程发来的call（RpcFunction 82行）
 */
public class RpcServerMessageManager extends ServerMessageManager {
    public RpcServerMessageManager(String nodeId) {
        super(nodeId);
    }

    @Override
    public void pulse() {
        super.pulse();
        // 服务心跳
        ServiceManager.pulse();
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
        LogCore.RpcServer.debug("recv call, callId = {}, messageId = { {} }, cur NodeId = { {} }, from NodeId = { {} }, data = { {} }", message.getRpcId(), message.getMessageId(), getNodeId(), message.getNodeId(), message.getData());
        CallUtils.handler(message);
    }
}
