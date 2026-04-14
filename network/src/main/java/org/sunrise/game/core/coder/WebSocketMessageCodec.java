package org.sunrise.game.core.coder;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import org.sunrise.game.log.LogCore;
import org.sunrise.game.core.message.SocketMessage;
import org.sunrise.game.utils.Utils;

import java.util.List;

public class WebSocketMessageCodec extends MessageToMessageCodec<BinaryWebSocketFrame, SocketMessage> {
    @Override
    protected void encode(ChannelHandlerContext ctx, SocketMessage message, List<Object> out) {
        // 使用默认 buffer 。如果没有做任何配置，通常默认实现为池化的 direct （直接内存，也称为堆外内存）
        ByteBuf byteBuf = ctx.alloc().buffer();
        byteBuf.writeInt(message.getMessageType());
        byteBuf.writeInt(message.getData().length);
        byteBuf.writeBytes(message.getData());

        BinaryWebSocketFrame socketFrame = new BinaryWebSocketFrame(byteBuf);
        out.add(socketFrame);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, BinaryWebSocketFrame binary, List<Object> out) {
        ByteBuf in = binary.content();
        if (in.readableBytes() < 8) {
            return;
        }

        in.markReaderIndex();
        int messageType = in.readInt();
        int dataLength = in.readInt();

        if (dataLength < 0 || dataLength > Utils.MAX_BODY_SIZE) {
            LogCore.BaseServer.warn("recv dataLength error : { {} }, will close, remoteAddress = { {} }", dataLength, ctx.channel().remoteAddress());
            // 跳过所有剩余字节，停止循环
            in.skipBytes(in.readableBytes());
            ctx.close();
            return;
        }

        if (in.readableBytes() < dataLength) {
            in.resetReaderIndex();
            return;
        }

        byte[] data = new byte[dataLength];
        in.readBytes(data);

        SocketMessage socketMessage = new SocketMessage();
        socketMessage.setMessageType(messageType);
        socketMessage.setData(data);

        out.add(socketMessage);
    }
}
