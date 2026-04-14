package core.client;

import com.google.protobuf.ByteString;
import io.netty.channel.Channel;
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
    protected boolean connectSuccess = false;
    protected String uid;
    protected String humanId;
    protected Channel channel;
    protected boolean startSend = false;
    protected long loginStartTime = 0L;
    protected long pingStartTime = 0L;

    public abstract void connect(String host, int port);

    public boolean sendMsg(TopicProto.TOPIC packetType, int packetId, ByteString bytes) {
        if (!channel.isActive()) {
            return false;
        }
        TopicProto.MBasePacketData.Builder msg = TopicProto.MBasePacketData.newBuilder();
        msg.setPacketType(packetType);
        msg.setPacketId(packetId);
        if (bytes != null) {
            msg.setPacketData(bytes);
        }
        String topicName = ProtocolRouter.getTopicName(packetType.getNumber());
        LogCore.Client.debug("sendMsg: uid={}, topic={}({}), packetId={}",
                uid, topicName, packetType.getNumber(), packetId);
        channel.writeAndFlush(new SocketMessage(MessageType.biz, msg.build().toByteArray()));
        return true;
    }

    public boolean sendMsg(int packetType, int packetId, ByteString bytes) {
        TopicProto.TOPIC topic = TopicProto.TOPIC.forNumber(packetType);
        if (topic == null) {
            return false;
        }
        return sendMsg(topic, packetId, bytes);
    }
}
