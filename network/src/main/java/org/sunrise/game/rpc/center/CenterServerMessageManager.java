package org.sunrise.game.rpc.center;

import org.sunrise.game.core.message.BaseMessage;
import org.sunrise.game.core.message.MessageUtils;
import org.sunrise.game.core.message.ServerMessageManager;
import org.sunrise.game.log.LogCore;

public class CenterServerMessageManager extends ServerMessageManager {

    public CenterServerMessageManager(String nodeId) {
        super(nodeId);
    }

    @Override
    protected void pulseHandlerOne(Object data) {
        var message = MessageUtils.fromMessage((byte[]) data, BaseMessage.class);
        LogCore.BaseServer.debug("recv msg, cur NodeId = { {} }, from NodeId = { {} }, messageId = { {} }, data = { {} }", getNodeId(), message.getNodeId(), message.getMessageId(), message.getMsg());
        NodeManager.updateNode(message);
    }
}
