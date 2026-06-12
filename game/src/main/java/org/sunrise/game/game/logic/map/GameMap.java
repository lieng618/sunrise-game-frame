package org.sunrise.game.game.logic.map;

import com.google.protobuf.Message;
import lombok.Getter;
import org.sunrise.game.game.human.HumanObject;
import org.sunrise.game.game.human.HumanObjectManager;
import org.sunrise.game.game.logic.monster.MonsterAi;
import org.sunrise.game.game.logic.unit.DropItemUnit;
import org.sunrise.game.game.logic.unit.GameUnit;
import org.sunrise.game.game.logic.unit.MonsterUnit;
import org.sunrise.game.game.logic.unit.PlayerUnit;
import org.sunrise.game.game.logic.unit.UnitUtils;
import org.sunrise.game.genProto.gen.MapProto;
import org.sunrise.game.genProto.gen.TopicProto;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * 单张地图的场景容器：管理单位进出、向玩家广播、同步场景，驱动怪物 AI。
 * 当地图内没有玩家时，怪物 AI 不 tick（无观察者，巡逻与位置广播均无意义）。
 */
public class GameMap {
    @Getter
    private final int mapId;

    /** 当前地图内的玩家单位，key 为 humanId */
    private final Map<String, PlayerUnit> players = new HashMap<>();

    /** 当前地图内的怪物单位，key 为 unitId */
    private final Map<String, MonsterUnit> monsters = new HashMap<>();

    /** 当前地图内的掉落物单位，key 为 unitId */
    private final Map<String, DropItemUnit> dropItems = new HashMap<>();

    public GameMap(int mapId) {
        this.mapId = mapId;
    }

    /**
     * 按 unitId 查找单位，先查玩家表再查怪物表。
     */
    public GameUnit getUnit(String unitId) {
        PlayerUnit player = players.get(unitId);
        if (player != null) {
            return player;
        }
        MonsterUnit monster = monsters.get(unitId);
        if (monster != null) {
            return monster;
        }
        return dropItems.get(unitId);
    }

    public PlayerUnit getPlayer(String unitId) {
        return players.get(unitId);
    }

    public MonsterUnit getMonster(String unitId) {
        return monsters.get(unitId);
    }

    public DropItemUnit getDropItem(String unitId) {
        return dropItems.get(unitId);
    }

    /** 获取地图上所有掉落物（只读视图） */
    public Collection<DropItemUnit> getDropItems() {
        return dropItems.values();
    }

    /** 当前地图是否有玩家在线（用于决定是否驱动怪物 AI、是否广播） */
    public boolean hasPlayers() {
        return !players.isEmpty();
    }

    /**
     * 单位进入地图，按 {@link org.sunrise.game.game.logic.unit.UnitType} 写入对应容器。
     */
    public void enterUnit(GameUnit unit) {
        switch (unit.getUnitType()) {
            case PLAYER -> enterPlayer((PlayerUnit) unit);
            case MONSTER -> enterMonster((MonsterUnit) unit);
            case DROP_ITEM -> enterDropItem((DropItemUnit) unit);
            default -> {
            }
        }
    }

    /**
     * 玩家进入地图，并向已在地图内的其他玩家广播进场。
     */
    public void enterPlayer(PlayerUnit player) {
        if (players.containsKey(player.getUnitId())) {
            return;
        }
        player.setMapId(mapId);
        players.put(player.getUnitId(), player);
        broadcastUnitEnter(player);
    }

    /**
     * 怪物进入地图。仅当地图内有玩家时才广播进场（无玩家时无需同步）。
     */
    public void enterMonster(MonsterUnit monster) {
        if (monsters.containsKey(monster.getUnitId())) {
            return;
        }
        monster.setMapId(mapId);
        monsters.put(monster.getUnitId(), monster);
        if (hasPlayers()) {
            broadcastUnitEnter(monster);
        }
    }

    /**
     * 掉落物进入地图，仅当地图内有玩家时才广播。
     */
    public void enterDropItem(DropItemUnit dropItem) {
        if (dropItems.containsKey(dropItem.getUnitId())) {
            return;
        }
        dropItem.setMapId(mapId);
        dropItems.put(dropItem.getUnitId(), dropItem);
        if (hasPlayers()) {
            broadcastUnitEnter(dropItem);
        }
    }

    /**
     * 单位离开地图，从对应容器中移除；有玩家在场时才广播离场。
     */
    public void leaveUnit(String unitId) {
        if (players.remove(unitId) != null) {
            broadcastUnitLeave(unitId);
            return;
        }
        if (monsters.remove(unitId) != null && hasPlayers()) {
            broadcastUnitLeave(unitId);
            return;
        }
        if (dropItems.remove(unitId) != null && hasPlayers()) {
            broadcastUnitLeave(unitId);
        }
    }

    /**
     * 地图 100ms 心跳：仅当地图内有玩家时，驱动存活怪物的 {@link MonsterAi}。
     *
     * <p>由 {@link org.sunrise.game.game.logic.system.MapSystem#pulsePer100Ms()} 统一调度。
     */
    public void pulsePer100Ms() {
        if (!hasPlayers()) {
            return;
        }
        long nowMs = System.currentTimeMillis();
        for (MonsterUnit monster : monsters.values()) {
            if (!monster.isAlive()) {
                continue;
            }
            MonsterAi ai = monster.getAi();
            if (ai != null) {
                ai.pulsePer100Ms(nowMs);
            }
        }
    }

    /** 向地图内所有玩家广播单位位置更新 */
    public void broadcastUnitPosition(GameUnit unit) {
        if (!hasPlayers()) {
            return;
        }
        var builder = MapProto.MS2C_UnitPositionUpdate.newBuilder()
                .setPosition(UnitUtils.toUnitPosition(unit));
        broadcastToAll(
                MapProto.FROM_SERVER.S2C_UnitPositionUpdate_VALUE, builder);
    }

    /** 向地图内所有玩家广播单位属性变更 */
    public void broadcastUnitAttributeUpdate(String unitId, Map<Integer, Double> changed) {
        if (!hasPlayers() || changed == null || changed.isEmpty()) {
            return;
        }
        var builder = MapProto.MS2C_UnitAttributeUpdate.newBuilder()
                .setAttributes(UnitUtils.toUnitAttributes(unitId, changed));
        broadcastToAll(
                MapProto.FROM_SERVER.S2C_UnitAttributeUpdate_VALUE, builder);
    }

    private void broadcastUnitEnter(GameUnit unit) {
        var builder = MapProto.MS2C_UnitEnter.newBuilder()
                .setUnit(UnitUtils.toUnitInfo(unit))
                .setPosition(UnitUtils.toUnitPosition(unit))
                .setAttributes(UnitUtils.toUnitAttributes(unit));
        broadcastToAll(
                MapProto.FROM_SERVER.S2C_UnitEnter_VALUE, builder);
    }

    private void broadcastUnitLeave(String unitId) {
        var builder = MapProto.MS2C_UnitLeave.newBuilder().setUnitId(unitId);
        broadcastToAll(
                MapProto.FROM_SERVER.S2C_UnitLeave_VALUE, builder);
    }

    /** 向指定玩家同步当前地图所有单位的信息、位置、属性（含玩家与怪物） */
    public void syncScene(HumanObject humanObject) {
        var builder = MapProto.MS2C_SceneSync.newBuilder();
        for (PlayerUnit player : players.values()) {
            builder.addUnits(UnitUtils.toUnitInfo(player));
            builder.addPositions(UnitUtils.toUnitPosition(player));
            builder.addUnitAttributes(UnitUtils.toUnitAttributes(player));
        }
        for (MonsterUnit monster : monsters.values()) {
            builder.addUnits(UnitUtils.toUnitInfo(monster));
            builder.addPositions(UnitUtils.toUnitPosition(monster));
            builder.addUnitAttributes(UnitUtils.toUnitAttributes(monster));
        }
        for (DropItemUnit drop : dropItems.values()) {
            builder.addUnits(UnitUtils.toUnitInfo(drop));
            builder.addPositions(UnitUtils.toUnitPosition(drop));
            builder.addUnitAttributes(UnitUtils.toUnitAttributes(drop));
        }
        humanObject.sendMsg(TopicProto.TOPIC.TOPIC_TYPE_MAP_VALUE,
                MapProto.FROM_SERVER.S2C_SceneSync_VALUE, builder);
    }

    private void broadcastToAll(int packetId, Message.Builder builder) {
        for (PlayerUnit player : players.values()) {
            HumanObject humanObject = HumanObjectManager.getHumanObject(player.getUnitId());
            if (humanObject != null) {
                humanObject.sendMsg(TopicProto.TOPIC.TOPIC_TYPE_MAP_VALUE, packetId, builder);
            }
        }
    }
}
