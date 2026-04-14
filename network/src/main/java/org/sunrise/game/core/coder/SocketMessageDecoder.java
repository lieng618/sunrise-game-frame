package org.sunrise.game.core.coder;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import org.sunrise.game.log.LogCore;
import org.sunrise.game.core.message.SocketMessage;
import org.sunrise.game.utils.Utils;

import java.util.List;

public class SocketMessageDecoder extends ByteToMessageDecoder {
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        // 确保有足够的数据进行解码
        if (in.readableBytes() < 8) { // 确保至少有 8 字节（4 字节的 messageType + 4 字节的 dataLength）
            return;
        }

        // 标记当前读取位置，以便不足时可以重置
        in.markReaderIndex();

        // 读取消息类型和数据长度
        int messageType = in.readInt();
        int dataLength = in.readInt();

        if (dataLength < 0 || dataLength > Utils.MAX_BODY_SIZE) {
            LogCore.BaseServer.warn("recv dataLength error : { {} }, will close, remoteAddress = { {} }", dataLength, ctx.channel().remoteAddress());
            // 跳过所有剩余字节，停止循环
            in.skipBytes(in.readableBytes());
            ctx.close();
            return;
        }

        // 检查是否接收到了完整的数据
        if (in.readableBytes() < dataLength) {
            in.resetReaderIndex();
            return; // 等待更多数据
        }

        // 读取 dataLength 长度的数据
        byte[] data = new byte[dataLength];
        in.readBytes(data);

        // 创建并添加 SocketMessage 到输出列表
        SocketMessage socketMessage = new SocketMessage();
        socketMessage.setMessageType(messageType);
        socketMessage.setData(data);

        out.add(socketMessage);
    }
}
