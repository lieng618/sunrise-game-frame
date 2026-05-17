package org.sunrise.game.core.message;

import org.sunrise.game.log.LogCore;
import org.sunrise.game.core.server.BaseServerManager;

/**
 * 服务器默认消息管理器
 * 处理消息
 */
public class ServerMessageManager extends BaseMessageManager {
    public ServerMessageManager(String nodeId) {
        super(nodeId);
    }

    @Override
    protected void pulseHandlerOne(Object data) {
        var message = MessageUtils.fromMessage((byte[]) data, BaseMessage.class);
        if (message == null) {
            return;
        }
        LogCore.BaseServer.debug("recv msg, cur NodeId = { {} }, from NodeId = { {} }, messageId = { {} }, data = { {} }", getNodeId(), message.getNodeId(), message.getMessageId(), message.getMsg());
    }
    @Override
    protected void pulseSenderOne(Object data) {
        BaseServerManager.sendMsgToClient((BaseMessage) data);
    }
}
