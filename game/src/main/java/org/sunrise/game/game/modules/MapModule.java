package org.sunrise.game.game.modules;

import com.alibaba.fastjson2.TypeReference;
import lombok.Getter;
import lombok.Setter;
import org.sunrise.game.game.annotation.HumanModule;
import org.sunrise.game.game.config.Tables;
import org.sunrise.game.game.config.map.TbMap;
import org.sunrise.game.game.logic.map.GameMap;
import org.sunrise.game.game.logic.system.GameSystemUtils;
import org.sunrise.game.game.logic.system.MapSystem;
import org.sunrise.game.genProto.gen.MapProto;
import org.sunrise.game.genProto.gen.TopicProto;

@HumanModule
@Getter
@Setter
public class MapModule extends BaseModule {
    private int mapId = 0;
    private int lastMapId;
    private float mapPostX;
    private float mapPostY;
    private float mapPostZ;
    private float Orientation;

    public MapModule(String humanId) {
        super(humanId);
    }

    @Override
    public void init() {
        mapId = 0;
        Orientation = 0;
        lastMapId = Tables.ConfigParam.getMapInitId();
        // 首次创建角色，放入初始化地图和坐标
        TbMap map = Tables.ConfigMap.get(lastMapId);
        mapPostX = map.enterX;
        mapPostY = map.enterY;
        mapPostZ = map.enterZ;
    }

    @Override
    public void load() {
        getDbData("id", new TypeReference<Integer>() {}, value -> lastMapId = value);
        getDbData("mapPostX", new TypeReference<Float>() {}, value -> mapPostX = value);
        getDbData("mapPostY", new TypeReference<Float>() {}, value -> mapPostY = value);
        getDbData("mapPostZ", new TypeReference<Float>() {}, value -> mapPostZ = value);
        getDbData("Orientation", new TypeReference<Float>() {}, value -> Orientation = value);
    }

    @Override
    public void save() {
        putDbData("id", mapId);
        putDbData("mapPostX", mapPostX);
        putDbData("mapPostY", mapPostY);
        putDbData("mapPostZ", mapPostZ);
        putDbData("Orientation", Orientation);
    }

    @Override
    public void sendToClient() {
        getHuman().sendMsg(TopicProto.TOPIC.TOPIC_TYPE_MAP_VALUE, MapProto.FROM_SERVER.S2C_LastMap_VALUE,
                MapProto.MS2C_LastMap.newBuilder().setId(lastMapId));
    }

    public void updatePosition(float x, float y, float z, float o) {
        this.mapPostX = x;
        this.mapPostY = y;
        this.mapPostZ = z;
        this.Orientation = o;
    }

    public void leaveMap() {
        if (mapId == 0) {
            return;
        }
        MapSystem mapSystem = GameSystemUtils.getSystem(MapSystem.class);
        GameMap gameMap = mapSystem.getMap(mapId);
        if (gameMap == null) {
            return;
        }

        gameMap.humanObjectLeave(this.getHumanId());
    }
}
