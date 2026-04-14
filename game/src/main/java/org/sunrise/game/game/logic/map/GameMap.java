package org.sunrise.game.game.logic.map;

import com.alibaba.fastjson2.JSON;
import com.google.protobuf.Message;
import org.sunrise.game.game.config.Tables;
import org.sunrise.game.game.config.map.TbMap;
import org.sunrise.game.game.human.HumanObject;
import org.sunrise.game.game.logic.mail.MailData;
import org.sunrise.game.game.modules.DataModule;
import org.sunrise.game.game.modules.MapModule;
import org.sunrise.game.genProto.gen.MapProto;
import org.sunrise.game.genProto.gen.TopicProto;
import org.sunrise.game.genRpc.gen.CallEnum;
import org.sunrise.game.rpc.function.RpcFunction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GameMap {

    private final int mapId;
    private final Map<String, HumanObject> humanObjectMap = new ConcurrentHashMap<>();

    public GameMap(int mapId) {
        this.mapId = mapId;
    }

    // 玩家进入地图
    public void humanObjectEnter(HumanObject humanObject) {
        // 已经在此地图中
        if (humanObjectMap.containsKey(humanObject.getHumanId())) {
            return;
        }
        humanObjectMap.put(humanObject.getHumanId(), humanObject);
        // 把自己的信息同步给其他人
        var syncBuilder = MapProto.MS2C_Sync.newBuilder();

        MapModule module = humanObject.getModule(MapModule.class);
        if (module.getMapId() != 0) {
            // 切换地图
            // 放到目标地图的进入坐标
            TbMap tbMap = Tables.ConfigMap.get(mapId);
            module.setMapPostX(tbMap.enterX);
            module.setMapPostY(tbMap.enterY);
            module.setMapPostZ(tbMap.enterZ);
        }
        module.setMapId(mapId);

        MapProto.STRoleInfo roleInfo = MapProto.STRoleInfo.newBuilder()
                .setHumanId(humanObject.getHumanId())
                .setName(humanObject.getModule(DataModule.class).getName())
                .setMapPostX(module.getMapPostX())
                .setMapPostY(module.getMapPostY())
                .setMapPostZ(module.getMapPostZ())
                .build();
        syncBuilder.addRoles(roleInfo);
        broadcastToAll(TopicProto.TOPIC.TOPIC_TYPE_MAP_VALUE, MapProto.FROM_SERVER.S2C_Enter_VALUE, syncBuilder);
        List<MailData.MailAttachment> attachments = new ArrayList<>();
        attachments.add(new MailData.MailAttachment(mapId, 10)); // 物品ID 1001，数量10
        attachments.add(new MailData.MailAttachment(1009, 100)); // 物品ID 1001，数量10
        RpcFunction rpcFunction = RpcFunction.newInstance();
        rpcFunction.call(CallEnum.MailService_sendMail, "humanId", humanObject.getHumanId(), "templateId", 1, "attachmentsJson", JSON.toJSONString(attachments), "senderName", "test");
    }

    // 玩家离开地图
    public void humanObjectLeave(String humanId) {
        HumanObject humanObject = humanObjectMap.get(humanId);
        if (humanObject == null) {
            return;
        }
        var leaveMessage = MapProto.MS2C_Leave.newBuilder().setHumanId(humanObject.getHumanId());
        broadcastToAll(TopicProto.TOPIC.TOPIC_TYPE_MAP_VALUE, MapProto.FROM_SERVER.S2C_Leave_VALUE, leaveMessage);
        humanObjectMap.remove(humanId);
    }

    // 广播给地图中所有玩家
    public void broadcastToAll(int packetType, int packetId, Message.Builder builder) {
        for (HumanObject humanObject : humanObjectMap.values()) {
            humanObject.sendMsg(packetType, packetId, builder);
        }
    }

    // 广播地图中的玩家信息
    public void sync(HumanObject humanObject) {
        var syncBuilder = MapProto.MS2C_Sync.newBuilder();
        for (HumanObject ho : humanObjectMap.values()) {
            MapModule module = ho.getModule(MapModule.class);
            MapProto.STRoleInfo roleInfo = MapProto.STRoleInfo.newBuilder()
                    .setHumanId(ho.getHumanId())
                    .setName(humanObject.getModule(DataModule.class).getName())
                    .setMapPostX(module.getMapPostX())
                    .setMapPostY(module.getMapPostY())
                    .setMapPostZ(module.getMapPostZ())
                    .setOrientation(module.getOrientation())
                    .build();
            syncBuilder.addRoles(roleInfo);
        }
        humanObject.sendMsg(TopicProto.TOPIC.TOPIC_TYPE_MAP_VALUE, MapProto.FROM_SERVER.S2C_Sync_VALUE, syncBuilder);
    }
}
