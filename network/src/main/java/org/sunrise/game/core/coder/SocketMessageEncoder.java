package org.sunrise.game.core.coder;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import org.sunrise.game.core.message.SocketMessage;

public class SocketMessageEncoder extends MessageToByteEncoder<SocketMessage> {
    @Override
    protected void encode(ChannelHandlerContext ctx, SocketMessage msg, ByteBuf out) throws Exception {
        out.writeInt(msg.getMessageType());
        out.writeInt(msg.getData().length);
        out.writeBytes(msg.getData());
    }
}
