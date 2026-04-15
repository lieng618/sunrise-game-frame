package org.sunrise.game.core.client;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.Getter;
import org.sunrise.game.log.LogCore;
import org.sunrise.game.core.message.BaseMessage;
import org.sunrise.game.core.message.MessageType;
import org.sunrise.game.core.message.MessageUtils;
import org.sunrise.game.core.message.SocketMessage;

@Getter
public class BaseClientPulseHandler extends ChannelInboundHandlerAdapter {
    private final String nodeId;

    public BaseClientPulseHandler(String nodeId) {
        this.nodeId = nodeId;
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            BaseMessage pulseMsg = new BaseMessage(nodeId);
            pulseMsg.setMsg(System.currentTimeMillis());
            ctx.writeAndFlush(new SocketMessage(MessageType.idle, MessageUtils.toBytes(pulseMsg)));
            LogCore.BasePulse.debug("send ping, cur NodeId = { {} }", nodeId);
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }
}