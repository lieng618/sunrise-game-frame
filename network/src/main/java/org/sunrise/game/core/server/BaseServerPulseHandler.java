package org.sunrise.game.core.server;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.sunrise.game.log.LogCore;
import org.sunrise.game.core.message.BaseMessage;
import org.sunrise.game.core.message.MessageType;
import org.sunrise.game.core.message.MessageUtils;
import org.sunrise.game.core.message.SocketMessage;

public class BaseServerPulseHandler extends SimpleChannelInboundHandler<SocketMessage> {
    private final String nodeId;

    public BaseServerPulseHandler(String nodeId) {
        this.nodeId = nodeId;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, SocketMessage socketMessage) throws Exception {
        if (socketMessage.getMessageType() == MessageType.idle) {
            var message = MessageUtils.fromMessage(socketMessage.getData(), BaseMessage.class);
            LogCore.BasePulse.debug("recv ping, cur NodeId = { {} }, from NodeId = { {} }, delay = {} ms", nodeId, message.getNodeId(), System.currentTimeMillis() - (long)message.getMsg());
            return;
        }
        ctx.fireChannelRead(socketMessage);
    }
}