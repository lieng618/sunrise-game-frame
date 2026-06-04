package org.sunrise.game.game.logic.map;

import com.google.protobuf.Message;
import lombok.Getter;
import org.sunrise.game.game.human.HumanObject;
import org.sunrise.game.game.human.HumanObjectManger;
import org.sunrise.game.game.logic.unit.GameUnit;
import org.sunrise.game.game.logic.unit.UnitType;
import org.sunrise.game.game.logic.unit.UnitUtils;
import org.sunrise.game.genProto.gen.MapProto;
import org.sunrise.game.genProto.gen.TopicProto;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class GameMap {

    @Getter
    private final int mapId;
    private final Map<String, GameUnit> units = new HashMap<>();

    public GameMap(int mapId) {
        this.mapId = mapId;
    }

    public GameUnit getUnit(String unitId) {
        return units.get(unitId);
    }

    public Collection<GameUnit> getUnits() {
        return units.values();
    }

    public void enterUnit(GameUnit unit) {
        if (units.containsKey(unit.getUnitId())) {
            return;
        }
        unit.setMapId(mapId);
        units.put(unit.getUnitId(), unit);
        broadcastUnitEnter(unit);
    }

    public void leaveUnit(String unitId) {
        if (!units.containsKey(unitId)) {
            return;
        }
        units.remove(unitId);
        broadcastUnitLeave(unitId);
    }

    public void broadcastUnitPosition(GameUnit unit) {
        var builder = MapProto.MS2C_UnitPositionUpdate.newBuilder()
                .setPosition(UnitUtils.toUnitPosition(unit));
        broadcastToAll(TopicProto.TOPIC.TOPIC_TYPE_MAP_VALUE,
                MapProto.FROM_SERVER.S2C_UnitPositionUpdate_VALUE, builder);
    }

    public void broadcastUnitAttributeUpdate(String unitId, Map<Integer, Double> changed) {
        if (changed == null || changed.isEmpty()) {
            return;
        }
        var builder = MapProto.MS2C_UnitAttributeUpdate.newBuilder()
                .setAttributes(UnitUtils.toUnitAttributes(unitId, changed));
        broadcastToAll(TopicProto.TOPIC.TOPIC_TYPE_MAP_VALUE,
                MapProto.FROM_SERVER.S2C_UnitAttributeUpdate_VALUE, builder);
    }

    public void broadcastUnitEnter(GameUnit unit) {
        var builder = MapProto.MS2C_UnitEnter.newBuilder()
                .setUnit(UnitUtils.toUnitInfo(unit))
                .setPosition(UnitUtils.toUnitPosition(unit))
                .setAttributes(UnitUtils.toUnitAttributes(unit));
        broadcastToAll(TopicProto.TOPIC.TOPIC_TYPE_MAP_VALUE,
                MapProto.FROM_SERVER.S2C_UnitEnter_VALUE, builder);
    }

    public void broadcastUnitLeave(String unitId) {
        var builder = MapProto.MS2C_UnitLeave.newBuilder().setUnitId(unitId);
        broadcastToAll(TopicProto.TOPIC.TOPIC_TYPE_MAP_VALUE,
                MapProto.FROM_SERVER.S2C_UnitLeave_VALUE, builder);
    }

    /** 向指定玩家同步当前地图所有单位的信息、位置、属性 */
    public void syncScene(HumanObject humanObject) {
        var builder = MapProto.MS2C_SceneSync.newBuilder();
        for (GameUnit unit : units.values()) {
            builder.addUnits(UnitUtils.toUnitInfo(unit));
            builder.addPositions(UnitUtils.toUnitPosition(unit));
            builder.addUnitAttributes(UnitUtils.toUnitAttributes(unit));
        }
        humanObject.sendMsg(TopicProto.TOPIC.TOPIC_TYPE_MAP_VALUE,
                MapProto.FROM_SERVER.S2C_SceneSync_VALUE, builder);
    }

    public void broadcastToAll(int packetType, int packetId, Message.Builder builder) {
        for (GameUnit unit : units.values()) {
            if (unit.getUnitType() != UnitType.PLAYER) {
                continue;
            }
            HumanObject humanObject = HumanObjectManger.getHumanObject(unit.getUnitId());
            if (humanObject != null) {
                humanObject.sendMsg(packetType, packetId, builder);
            }
        }
    }
}
