package org.sunrise.game.game.logic.drop;

import org.sunrise.game.game.annotation.GameSystem;
import org.sunrise.game.game.logic.drop.DropCalculator.DropResult;
import org.sunrise.game.game.logic.map.GameMap;
import org.sunrise.game.game.logic.system.BaseSystem;
import org.sunrise.game.game.logic.system.GameSystemUtils;
import org.sunrise.game.game.logic.system.MapSystem;
import org.sunrise.game.game.logic.unit.DropItemUnit;
import org.sunrise.game.log.LogCore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 掉落系统：怪物死亡时根据配置生成掉落物，管理掉落物生命周期。
 * <p>
 * 使用两级掉落：{@link DropCalculator#roll(int)} 查 DropGroup → Drop，计算掉落结果。
 * <p>
 * 每秒清理过期掉落物。
 */
@GameSystem
public class DropSystem extends BaseSystem {

    @Override
    public void init() {
        LogCore.GameServer.info("DropSystem init");
    }

    /**
     * 怪物死亡时调用，根据怪物配置的 dropId 生成掉落物。
     *
     * @param dropId        掉落id
     * @param mapId         怪物所在的地图ID
     * @param posX          死亡位置X
     * @param posY          死亡位置Y
     * @param posZ          死亡位置Z
     * @param killerHumanId 击杀者 humanId（用于拾取保护，可为 null）
     * @return 生成的掉落物列表
     */
    public List<DropItemUnit> generateDrops(int dropId, int mapId,
                                            float posX, float posY, float posZ,
                                            String killerHumanId) {
        List<DropResult> results = DropCalculator.roll(dropId);
        if (results.isEmpty()) {
            return Collections.emptyList();
        }

        GameMap gameMap = GameSystemUtils.getSystem(MapSystem.class).getMap(mapId);
        if (gameMap == null) {
            LogCore.GameServer.warn("DropSystem.generateDrops: map not found, mapId={}", mapId);
            return Collections.emptyList();
        }

        List<DropItemUnit> dropUnits = new ArrayList<>();
        for (DropResult r : results) {
            DropItemUnit dropUnit = new DropItemUnit(r.itemId(), r.count());
            dropUnit.getPosition().set(posX, posY, posZ, 0);
            dropUnit.setProtectorHumanId(killerHumanId);

            gameMap.enterUnit(dropUnit);
            dropUnits.add(dropUnit);

            LogCore.GameServer.debug("Drop generated: itemId={}, count={}, mapId={}", r.itemId(), r.count(), mapId);
        }
        return dropUnits;
    }

    /**
     * 每秒清理过期掉落物
     */
    @Override
    public void pulsePerSec() {
        MapSystem mapSystem = GameSystemUtils.getSystem(MapSystem.class);
        if (mapSystem == null) return;
        for (GameMap gameMap : mapSystem.getAllMaps().values()) {
            cleanExpiredDrops(gameMap);
        }
    }

    private void cleanExpiredDrops(GameMap gameMap) {
        List<String> toRemove = new ArrayList<>();
        for (DropItemUnit drop : gameMap.getDropItems()) {
            if (drop.isExpired()) {
                toRemove.add(drop.getUnitId());
            }
        }
        for (String unitId : toRemove) {
            gameMap.leaveUnit(unitId);
            LogCore.GameServer.debug("Drop expired and removed: unitId={}", unitId);
        }
    }
}
