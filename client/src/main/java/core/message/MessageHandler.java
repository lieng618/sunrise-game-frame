package core.message;

import com.google.protobuf.ByteString;
import core.client.SocketClient;
import core.client.SocketClientManager;
import core.message.annotation.Handler;
import org.sunrise.game.genProto.gen.LoginProto;
import org.sunrise.game.genProto.gen.TopicProto;
import org.sunrise.game.log.LogCore;

public class MessageHandler {

    public static void handler(String uid, byte[] bytes) {
        SocketClient client = SocketClientManager.getClient(uid);
        if (client == null) {
            return;
        }
        TopicProto.MBasePacketData packet;
        try {
            packet = TopicProto.MBasePacketData.parseFrom(bytes);
            ProtocolRouter.route(client, packet);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Handler(packetType = TopicProto.TOPIC.TOPIC_TYPE_LOGIN_VALUE, packetId = LoginProto.FROM_SERVER.S2C_Login_VALUE)
    public static void LOGIN_S2C_UserLogin(SocketClient client) {
        client.sendMsg(TopicProto.TOPIC.TOPIC_TYPE_LOGIN, LoginProto.FROM_CLIENT.C2S_HumanList_VALUE, null);
    }

    @Handler(packetType = TopicProto.TOPIC.TOPIC_TYPE_LOGIN_VALUE, packetId = LoginProto.FROM_SERVER.S2C_HumanList_VALUE)
    public static void LOGIN_S2C_HumanList(SocketClient client) {
        ByteString data = LoginProto.MC2S_SelectHuman.newBuilder()
                .setPos(0)
                .setServerId(1)
                .build()
                .toByteString();
        client.sendMsg(TopicProto.TOPIC.TOPIC_TYPE_LOGIN, LoginProto.FROM_CLIENT.C2S_SelectHuman_VALUE, data);
    }

    @Handler(packetType = TopicProto.TOPIC.TOPIC_TYPE_LOGIN_VALUE, packetId = LoginProto.FROM_SERVER.S2C_SelectHuman_VALUE)
    public static void LOGIN_S2C_SelectHuman(SocketClient client) {
        LogCore.Client.info("login success, uid = {}, use = {} ms",
                client.getUid(), System.currentTimeMillis() - client.getLoginStartTime());
    }

    @Handler(packetType = TopicProto.TOPIC.TOPIC_TYPE_LOGIN_VALUE, packetId = LoginProto.FROM_SERVER.S2C_Kick_VALUE)
    public static void LOGIN_S2C_Kick(SocketClient client) {
        LogCore.Client.warn("Received kick message, closing connection for uid: {}", client.getUid());
        if (client.getChannel() != null && client.getChannel().isActive()) {
            client.getChannel().close();
        }
    }
}
