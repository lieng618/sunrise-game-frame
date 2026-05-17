package org.sunrise.game.core.message;

import org.sunrise.game.core.client.BaseClientManager;
import org.sunrise.game.log.LogCore;

/**
 * 客户端默认消息管理器
 * 处理消息
 */
public class ClientMessageManager extends BaseMessageManager {
    public ClientMessageManager(String nodeId) {
        super(nodeId);
    }

    @Override
    protected void pulseHandlerOne(Object data) {
        var message = MessageUtils.fromMessage((byte[]) data, BaseMessage.class);
        if (message == null) {
            return;
        }
        LogCore.BaseClient.debug("recv msg, cur NodeId = { {} }, from NodeId = { {} }, messageId = { {} }, data = { {} }", getNodeId(), message.getNodeId(), message.getMessageId(), message.getMsg());
    }
    @Override
    protected void pulseSenderOne(Object data) {
        BaseClientManager.sendMsgToServer((BaseMessage) data);
    }
}
