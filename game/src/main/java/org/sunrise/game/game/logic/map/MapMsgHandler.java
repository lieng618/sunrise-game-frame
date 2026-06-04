package org.sunrise.game.game.logic.map;

import org.sunrise.game.game.annotation.MsgHandlerClass;
import org.sunrise.game.game.annotation.MsgHandlerMethod;
import org.sunrise.game.game.config.Tables;
import org.sunrise.game.game.config.map.TbMap;
import org.sunrise.game.game.human.HumanObject;
import org.sunrise.game.game.logic.system.GameSystemUtils;
import org.sunrise.game.game.logic.system.MapSystem;
import org.sunrise.game.game.logic.unit.PlayerUnit;
import org.sunrise.game.game.modules.PlayerUnitModule;
import org.sunrise.game.genProto.gen.MapProto;
import org.sunrise.game.genProto.gen.TopicProto;

@MsgHandlerClass(packetType = TopicProto.TOPIC.TOPIC_TYPE_MAP_VALUE)
public class MapMsgHandler {

    /**
     * 玩家进入地图
     */
    @MsgHandlerMethod(packetId = MapProto.FROM_CLIENT.C2S_Enter_VALUE)
    public static void enter(HumanObject humanObject, MapProto.MC2S_Enter data) {
        int targetMapId = data.getId();
        MapSystem mapSystem = GameSystemUtils.getSystem(MapSystem.class);
        GameMap targetMap = mapSystem.getMap(targetMapId);
        if (targetMap == null) {
            return;
        }

        PlayerUnitModule unitModule = humanObject.getModule(PlayerUnitModule.class);
        int previousMapId = unitModule.getMapId();
        // 已经在目标地图
        // 客户端重复进入或者掉线重连，同步一下场景信息
        if (previousMapId == targetMapId) {
            targetMap.syncScene(humanObject);
            return;
        }
        // 离开旧场景
        unitModule.leaveMap();

        int lastMapId = unitModule.getLastMapId();
        // 本次要去新地图了，设置新地图的进入坐标
        if (lastMapId != targetMapId) {
            TbMap tbMap = Tables.ConfigMap.get(targetMapId);
            unitModule.getPosition().set(tbMap.enterX, tbMap.enterY, tbMap.enterZ, unitModule.getPosition().getOrientation());
        }
        PlayerUnit playerUnit = new PlayerUnit(unitModule.getHumanId());
        targetMap.enterUnit(playerUnit);
        unitModule.setMapId(targetMapId);
        unitModule.setLastMapId(targetMapId);

        targetMap.syncScene(humanObject);
    }

    /**
     * 玩家移动
     */
    @MsgHandlerMethod(packetId = MapProto.FROM_CLIENT.C2S_Move_VALUE)
    public static void move(HumanObject humanObject, MapProto.MC2S_Move data) {
        PlayerUnitModule unitModule = humanObject.getModule(PlayerUnitModule.class);
        if (unitModule.getMapId() == 0) {
            return;
        }
        MapSystem mapSystem = GameSystemUtils.getSystem(MapSystem.class);
        GameMap gameMap = mapSystem.getMap(unitModule.getMapId());
        if (gameMap == null) {
            return;
        }
        PlayerUnit playerUnit = gameMap.getPlayer(humanObject.getHumanId());
        if (playerUnit == null) {
            return;
        }

        MapNavData navData = MapNavUtils.get(gameMap.getMapId());
        if (navData != null && navData.isBlocked(data.getMapPostX(), data.getMapPostY())) {
            return;
        }

        playerUnit.getPosition().set(
                data.getMapPostX(),
                data.getMapPostY(),
                data.getMapPostZ(),
                data.getOrientation());
        gameMap.broadcastUnitPosition(playerUnit);
    }

    /**
     * 玩家离开地图
     */
    @MsgHandlerMethod(packetId = MapProto.FROM_CLIENT.C2S_Leave_VALUE)
    public static void leave(HumanObject humanObject, MapProto.MC2S_Leave data) {
        PlayerUnitModule unitModule = humanObject.getModule(PlayerUnitModule.class);
        if (unitModule.getMapId() == 0) {
            return;
        }
        if (unitModule.getMapId() != data.getId()) {
            return;
        }
        unitModule.leaveMap();
    }
}
