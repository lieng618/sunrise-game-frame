package org.sunrise.game.game.logic.map;

import org.sunrise.game.game.annotation.MsgHandlerClass;
import org.sunrise.game.game.annotation.MsgHandlerMethod;
import org.sunrise.game.game.human.HumanObject;
import org.sunrise.game.game.logic.system.GameSystem;
import org.sunrise.game.game.logic.system.MapSystem;
import org.sunrise.game.game.modules.MapModule;
import org.sunrise.game.genProto.gen.MapProto;
import org.sunrise.game.genProto.gen.TopicProto;

@MsgHandlerClass(packetType = TopicProto.TOPIC.TOPIC_TYPE_MAP_VALUE)
public class MapMsgHandler {
    /**
     * 玩家进入地图
     */
    @MsgHandlerMethod(packetId = MapProto.FROM_CLIENT.C2S_Enter_VALUE)
    public static void enter(HumanObject humanObject, MapProto.MC2S_Enter data) {
        int mapId = data.getId();
        MapSystem mapSystem = GameSystem.getSystem(MapSystem.class);
        GameMap gameMap = mapSystem.getMap(mapId);
        if (gameMap == null) {
            return;
        }

        gameMap.humanObjectEnter(humanObject);
        gameMap.sync(humanObject); // 给此玩家同步地图中的所有玩家信息
    }

    /**
     * 玩家移动
     */
    @MsgHandlerMethod(packetId = MapProto.FROM_CLIENT.C2S_Move_VALUE)
    public static void move(HumanObject humanObject, MapProto.MC2S_Move data) {
        MapModule module = humanObject.getModule(MapModule.class);
        if (module.getMapId() == 0) {
            return;
        }
        MapSystem mapSystem = GameSystem.getSystem(MapSystem.class);
        GameMap gameMap = mapSystem.getMap(module.getMapId());
        if (gameMap == null) {
            return;
        }
        module.updatePosition(data.getMapPostX(), data.getMapPostY(), data.getMapPostZ(), data.getOrientation());

        var moveMessage = MapProto.MS2C_Move.newBuilder()
                .setMapPostX(data.getMapPostX())
                .setMapPostY(data.getMapPostY())
                .setMapPostZ(data.getMapPostZ())
                .setHumanId(humanObject.getHumanId());
        // 向当前地图中的所有玩家广播移动消息
        gameMap.broadcastToAll(TopicProto.TOPIC.TOPIC_TYPE_MAP_VALUE, MapProto.FROM_SERVER.S2C_Move_VALUE, moveMessage);
    }

    /**
     * 玩家离开地图
     */
    @MsgHandlerMethod(packetId = MapProto.FROM_CLIENT.C2S_Leave_VALUE)
    public static void leave(HumanObject humanObject, MapProto.MC2S_Leave data) {
        MapModule module = humanObject.getModule(MapModule.class);
        if (module.getMapId() == 0) {
            return;
        }
        int mapId = data.getId();
        MapSystem mapSystem = GameSystem.getSystem(MapSystem.class);
        GameMap gameMap = mapSystem.getMap(mapId);
        if (gameMap == null) {
            return;
        }

        gameMap.humanObjectLeave(humanObject.getHumanId());
    }
}

