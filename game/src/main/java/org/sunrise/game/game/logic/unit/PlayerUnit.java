package org.sunrise.game.game.logic.unit;

import lombok.Getter;
import org.sunrise.game.game.human.HumanObjectManager;
import org.sunrise.game.game.logic.attribute.AttributeContainer;
import org.sunrise.game.game.modules.PlayerUnitModule;

/**
 * 玩家场景单位，属性与位置数据存于 PlayerUnitModule。
 */
@Getter
public class PlayerUnit implements GameUnit {
    private final String humanId;

    public PlayerUnit(String humanId) {
        this.humanId = humanId;
    }

    private PlayerUnitModule module() {
        return HumanObjectManager.getHumanObject(humanId).getModule(PlayerUnitModule.class);
    }

    @Override
    public String getUnitId() {
        return humanId;
    }

    @Override
    public UnitType getUnitType() {
        return UnitType.PLAYER;
    }

    @Override
    public int getMapId() {
        return module().getMapId();
    }

    @Override
    public void setMapId(int mapId) {
        module().setMapId(mapId);
    }

    @Override
    public AttributeContainer getAttributeContainer() {
        return module().getContainer();
    }

    @Override
    public Position getPosition() {
        return module().getPosition();
    }

    @Override
    public int getConfigId() {
        return 0;
    }

}
