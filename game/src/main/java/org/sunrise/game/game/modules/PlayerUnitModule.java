package org.sunrise.game.game.modules;

import com.alibaba.fastjson2.TypeReference;
import lombok.Getter;
import lombok.Setter;
import org.sunrise.game.game.annotation.HumanModule;
import org.sunrise.game.game.config.Tables;
import org.sunrise.game.game.config.map.TbMap;
import org.sunrise.game.game.human.HumanObject;
import org.sunrise.game.game.logic.attribute.AttributeContainer;
import org.sunrise.game.game.logic.attribute.AttributeProvider;
import org.sunrise.game.game.logic.map.GameMap;
import org.sunrise.game.game.logic.system.GameSystemUtils;
import org.sunrise.game.game.logic.system.MapSystem;
import org.sunrise.game.game.logic.unit.GameUnit;
import org.sunrise.game.game.logic.unit.Position;
import org.sunrise.game.game.logic.unit.UnitUtils;
import org.sunrise.game.genProto.gen.MapProto;
import org.sunrise.game.genProto.gen.TopicProto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 玩家场景单位数据：属性、位置、地图。
 */
@HumanModule
@Getter
@Setter
public class PlayerUnitModule extends BaseModule {
    private final AttributeContainer container = new AttributeContainer();
    private final Position position = new Position();

    /** 当前所在地图（运行时，不存档） */
    private int mapId;
    /** 上次所在地图（存档） */
    private int lastMapId;

    public PlayerUnitModule(String humanId) {
        super(humanId);
    }

    @Override
    public void init() {
        mapId = 0;
        UnitUtils.initPlayerDefaultAttributes(container);
        container.recalculate();

        lastMapId = Tables.ConfigParam.getMapInitId();
        TbMap map = Tables.ConfigMap.get(lastMapId);
        position.set(map.enterX, map.enterY, map.enterZ, 0);
    }

    @Override
    public void load() {
        getDbData("baseValues", new TypeReference<Map<Integer, Double>>() {
        }, value -> {
            if (value != null) {
                for (Map.Entry<Integer, Double> entry : value.entrySet()) {
                    container.setBaseValue(entry.getKey(), entry.getValue());
                }
                container.recalculate();
            }
        });
        getDbData("id", new TypeReference<Integer>() {}, value -> lastMapId = value);
        getDbData("mapPostX", new TypeReference<>() {}, position::setX);
        getDbData("mapPostY", new TypeReference<>() {}, position::setY);
        getDbData("mapPostZ", new TypeReference<>() {}, position::setZ);
        getDbData("Orientation", new TypeReference<>() {}, position::setOrientation);
    }

    @Override
    public void save() {
        putDbData("baseValues", container.getBaseValues());
        putDbData("id", lastMapId);
        putDbData("mapPostX", position.getX());
        putDbData("mapPostY", position.getY());
        putDbData("mapPostZ", position.getZ());
        putDbData("Orientation", position.getOrientation());
    }

    @Override
    public void sendToClient() {
        getHuman().sendMsg(TopicProto.TOPIC.TOPIC_TYPE_MAP_VALUE, MapProto.FROM_SERVER.S2C_LastMap_VALUE,
                MapProto.MS2C_LastMap.newBuilder().setId(lastMapId));
    }

    @Override
    public void pulse() {
        if (!container.isDirty()) {
            return;
        }
        Map<Integer, Double> changed = recalculate();
        broadcastAttributeIfInMap(changed);
    }

    public void markDirty() {
        container.markDirty();
    }

    public Map<Integer, Double> recalculate() {
        List<AttributeProvider> attributeProviders = new ArrayList<>();
        HumanObject humanObject = getHuman();
        if (humanObject == null) {
            return container.resetProviders(attributeProviders);
        }
        for (BaseModule baseModule : humanObject.getModules().values()) {
            if (baseModule.getAttribute() != null) {
                attributeProviders.add(baseModule.getAttribute());
            }
        }
        return container.resetProviders(attributeProviders);
    }

    public void leaveMap() {
        if (mapId == 0) {
            return;
        }
        lastMapId = mapId;
        MapSystem mapSystem = GameSystemUtils.getSystem(MapSystem.class);
        GameMap gameMap = mapSystem.getMap(mapId);
        if (gameMap != null) {
            gameMap.leaveUnit(getHumanId());
        }
        mapId = 0;
    }

    private void broadcastAttributeIfInMap(Map<Integer, Double> changed) {
        if (changed.isEmpty() || mapId == 0) {
            return;
        }
        HumanObject humanObject = getHuman();
        if (humanObject == null) {
            return;
        }
        MapSystem mapSystem = GameSystemUtils.getSystem(MapSystem.class);
        GameMap gameMap = mapSystem.getMap(mapId);
        if (gameMap == null) {
            return;
        }
        GameUnit unit = gameMap.getUnit(getHumanId());
        if (unit != null) {
            gameMap.broadcastUnitAttributeUpdate(unit.getUnitId(), changed);
        }
    }
}
