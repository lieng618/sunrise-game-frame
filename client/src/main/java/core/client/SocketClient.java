package core.client;

import com.google.protobuf.ByteString;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import kcp.Ukcp;
import lombok.Getter;
import lombok.Setter;
import core.message.ProtocolRouter;
import org.sunrise.game.core.message.MessageType;
import org.sunrise.game.core.message.SocketMessage;
import org.sunrise.game.genProto.gen.TopicProto;
import org.sunrise.game.log.LogCore;

@Getter
@Setter
public abstract class SocketClient {
    protected volatile boolean connectSuccess = false;
    protected String uid;
    protected String humanId;
    protected Channel channel;
    protected Ukcp ukcp;
    protected boolean startSend = false;
    protected long loginStartTime = 0L;
    protected long pingStartTime = 0L;

    public abstract void connect(String host, int port);

    public boolean sendMsg(TopicProto.TOPIC packetType, int packetId, ByteString bytes) {
        if (!isActive()) {
            return false;
        }
        TopicProto.MBasePacketData.Builder msg = TopicProto.MBasePacketData.newBuilder();
        msg.setPacketType(packetType);
        msg.setPacketId(packetId);
        if (bytes != null) {
            msg.setPacketData(bytes);
        }
        if (LogCore.Client.isDebugEnabled()) {
            String topicName = ProtocolRouter.getTopicName(packetType.getNumber());
            LogCore.Client.debug("sendMsg: uid={}, topic={}({}), packetId={}",
                    uid, topicName, packetType.getNumber(), packetId);
        }
        return writeBizBytes(msg.build().toByteArray(), true);
    }

    /**
     * 写入已序列化的 MBasePacketData 负载（压测等高频场景）
     */
    public boolean writeBizBytes(byte[] data, boolean flush) {
        if (!isActive() || data == null) {
            return false;
        }
        if (channel != null) {
            channel.write(new SocketMessage(MessageType.biz, data));
            if (flush) {
                channel.flush();
            }
            return true;
        }
        return writeBizBytesKcp(data);
    }

    /**
     * 复用同一 {@link SocketMessage} 实例写入（压测循环内避免每条 new 对象）
     */
    public boolean writeOutbound(SocketMessage msg, boolean flush) {
        if (!isActive() || msg == null) {
            return false;
        }
        if (channel != null) {
            channel.write(msg);
            if (flush) {
                channel.flush();
            }
            return true;
        }
        byte[] data = msg.getData();
        if (data == null) {
            return false;
        }
        return writeBizBytesKcp(data);
    }

    private boolean writeBizBytesKcp(byte[] data) {
        if (ukcp == null) {
            return false;
        }
        ByteBuf buf = Unpooled.buffer(8 + data.length);
        buf.writeInt(MessageType.biz);
        buf.writeInt(data.length);
        buf.writeBytes(data);
        ukcp.write(buf);
        buf.release();
        return true;
    }

    public void flushChannel() {
        if (channel != null && channel.isActive()) {
            channel.flush();
        }
    }

    public boolean isChannelWritable() {
        if (channel != null) {
            return channel.isWritable();
        }
        return true;
    }

    public boolean sendMsg(int packetType, int packetId, ByteString bytes) {
        TopicProto.TOPIC topic = TopicProto.TOPIC.forNumber(packetType);
        if (topic == null) {
            return false;
        }
        return sendMsg(topic, packetId, bytes);
    }

    public boolean isActive() {
        if (channel != null) {
            return channel.isActive();
        } else if (ukcp != null) {
            return ukcp.isActive();
        }
        return false;
    }

    public void close() {
        if (channel != null) {
            channel.close();
        } else if (ukcp != null) {
            ukcp.close();
        }
    }
}
