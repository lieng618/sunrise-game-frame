package core.message;

import com.google.protobuf.ByteString;
import core.client.SocketClient;
import core.client.SocketClientManager;
import core.client.StressManager;
import core.message.annotation.Handler;
import org.sunrise.game.genProto.gen.ItemProto;
import org.sunrise.game.genProto.gen.LoginProto;
import org.sunrise.game.genProto.gen.TopicProto;
import org.sunrise.game.log.LogCore;

public class MessageHandler {

    public static void handler(String uid, byte[] bytes) {
        SocketClient client = SocketClientManager.getClient(uid);
        if (client == null) {
            return;
        }
        if (StressManager.tryFastRouteStressResponse(client, bytes)) {
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
        if (!StressManager.isStressClient(client.getUid())) {
            LogCore.Client.info("login success, uid = {}, use = {} ms",
                    client.getUid(), System.currentTimeMillis() - client.getLoginStartTime());
        }
        StressManager.onSelectHuman(client);
    }

    @Handler(packetType = TopicProto.TOPIC.TOPIC_TYPE_LOGIN_VALUE, packetId = LoginProto.FROM_SERVER.S2C_ClientPing_VALUE)
    public static void LOGIN_S2C_ClientPing(SocketClient client) {
        StressManager.onPacketResponse(client, StressManager.PacketMode.PING);
    }

    @Handler(packetType = TopicProto.TOPIC.TOPIC_TYPE_ITEM_VALUE, packetId = ItemProto.FROM_SERVER.S2C_ItemList_VALUE)
    public static void ITEM_S2C_ItemList(SocketClient client) {
        StressManager.onPacketResponse(client, StressManager.PacketMode.BUSINESS_GET_ITEM_LIST);
    }

    @Handler(packetType = TopicProto.TOPIC.TOPIC_TYPE_LOGIN_VALUE, packetId = LoginProto.FROM_SERVER.S2C_Kick_VALUE)
    public static void LOGIN_S2C_Kick(SocketClient client) {
        LogCore.Client.warn("Received kick message, closing connection for uid: {}", client.getUid());
        if (client.isActive()) {
            client.close();
            SocketClientManager.removeClient(client.getUid());
        }
    }
}
