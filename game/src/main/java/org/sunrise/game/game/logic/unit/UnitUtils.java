package org.sunrise.game.game.logic.unit;

import org.sunrise.game.game.config.Enum.AttributeType;
import org.sunrise.game.game.config.Tables;
import org.sunrise.game.game.config.item.TbItem;
import org.sunrise.game.game.config.monster.TbMonster;
import org.sunrise.game.game.human.HumanObject;
import org.sunrise.game.game.human.HumanObjectManager;
import org.sunrise.game.game.logic.attribute.AttributeContainer;
import org.sunrise.game.game.modules.DataModule;
import org.sunrise.game.genProto.gen.MapProto;

import java.util.Map;

public final class UnitUtils {
    public static final int ATTRIBUTE_SCALE = 1000;
    private static long unitId = 0L;
    private static long dropUnitId = 0L;

    private UnitUtils() {
    }

    public static long genMonsterUnitId() {
        unitId++;
        return unitId;
    }

    public static long genDropUnitId() {
        dropUnitId++;
        return dropUnitId;
    }

    public static void initPlayerDefaultAttributes(AttributeContainer container) {
        container.setBaseValue(AttributeType.HP, 100);
        container.setBaseValue(AttributeType.MP, 100);
        container.setBaseValue(AttributeType.ATTACK, 10);
        container.setBaseValue(AttributeType.DEFENSE, 5);
        container.setBaseValue(AttributeType.SPEED, 100);
        container.setBaseValue(AttributeType.CRIT_RATE, 0.05);
        container.setBaseValue(AttributeType.CRIT_DAMAGE, 1.5);
        container.setBaseValue(AttributeType.DODGE, 0);
        container.setBaseValue(AttributeType.HIT, 1);
        container.setBaseValue(AttributeType.MAX_HP, 100);
        container.setBaseValue(AttributeType.MAX_MP, 100);
        container.setBaseValue(AttributeType.HP_RECOVER, 1);
        container.setBaseValue(AttributeType.MP_RECOVER, 1);
        container.setBaseValue(AttributeType.ATTACK_PERCENT, 0);
        container.setBaseValue(AttributeType.DEFENSE_PERCENT, 0);
        container.setBaseValue(AttributeType.HP_PERCENT, 0.01);
        container.setBaseValue(AttributeType.MP_PERCENT, 0.02);
        container.setBaseValue(AttributeType.SPEED_PERCENT, 0);
    }

    /** 从配置表读取怪物属性 */
    public static void initMonsterAttributes(MonsterUnit unit) {
        TbMonster cfg = Tables.ConfigMonster.get(unit.getMonsterId());
        if (cfg == null) {
            return;
        }
        AttributeContainer container = unit.getAttributeContainer();
        container.setBaseValue(AttributeType.HP, cfg.hp);
        container.setBaseValue(AttributeType.MAX_HP, cfg.hp);
        container.setBaseValue(AttributeType.MP, cfg.mp);
        container.setBaseValue(AttributeType.MAX_MP, cfg.mp);
        container.setBaseValue(AttributeType.ATTACK, cfg.attack);
        container.setBaseValue(AttributeType.DEFENSE, cfg.defense);
        container.setBaseValue(AttributeType.SPEED, cfg.speed);
        container.setBaseValue(AttributeType.HP_RECOVER, cfg.hpRecover);
        container.setBaseValue(AttributeType.MP_RECOVER, cfg.mpRecover);
        container.setBaseValue(AttributeType.ATTACK_PERCENT, cfg.attackPercent);
        container.setBaseValue(AttributeType.DEFENSE_PERCENT, cfg.defensePercent);
        container.setBaseValue(AttributeType.HP_PERCENT, cfg.hpPercent);
        container.setBaseValue(AttributeType.MP_PERCENT, cfg.mpPercent);
        container.setBaseValue(AttributeType.SPEED_PERCENT, cfg.speedPercent);
        container.recalculate();
    }


    public static String getUnitDisplayName(GameUnit unit) {
        if (unit.getUnitType() == UnitType.PLAYER) {
            HumanObject humanObject = HumanObjectManager.getHumanObject(unit.getUnitId());
            if (humanObject != null) {
                return humanObject.getModule(DataModule.class).getName();
            }
            return "";
        }
        if (unit.getUnitType() == UnitType.MONSTER) {
            TbMonster cfg = Tables.ConfigMonster.get(unit.getConfigId());
            return cfg != null ? cfg.name : "";
        }
        if (unit.getUnitType() == UnitType.DROP_ITEM) {
            DropItemUnit drop = (DropItemUnit) unit;
            TbItem cfg = Tables.ConfigItem.get(drop.getConfigId());
            String name = cfg != null ? cfg.name : "???";
            return name + " x" + drop.getCount();
        }
        return "";
    }

    public static MapProto.STUnitInfo toUnitInfo(GameUnit unit) {
        return MapProto.STUnitInfo.newBuilder()
                .setUnitId(unit.getUnitId())
                .setUnitType(unit.getUnitType().getValue())
                .setConfigId(unit.getConfigId())
                .setName(getUnitDisplayName(unit))
                .build();
    }

    public static MapProto.STUnitPosition toUnitPosition(GameUnit unit) {
        Position pos = unit.getPosition();
        return MapProto.STUnitPosition.newBuilder()
                .setUnitId(unit.getUnitId())
                .setPosX(pos.getX())
                .setPosY(pos.getY())
                .setPosZ(pos.getZ())
                .setOrientation(pos.getOrientation())
                .build();
    }

    public static MapProto.STUnitAttributes toUnitAttributes(String unitId, Map<Integer, Double> values) {
        MapProto.STUnitAttributes.Builder builder = MapProto.STUnitAttributes.newBuilder()
                .setUnitId(unitId);
        for (Map.Entry<Integer, Double> entry : values.entrySet()) {
            builder.addAttributes(MapProto.STAttributeValue.newBuilder()
                    .setAttributeType(entry.getKey())
                    .setFinalValue(toScaledLong(entry.getValue()))
                    .build());
        }
        return builder.build();
    }

    public static MapProto.STUnitAttributes toUnitAttributes(GameUnit unit) {
        AttributeContainer container = unit.getAttributeContainer();
        if (container == null) {
            return MapProto.STUnitAttributes.newBuilder().setUnitId(unit.getUnitId()).build();
        }
        if (container.isDirty()) {
            container.recalculate();
        }
        return toUnitAttributes(unit.getUnitId(), container.getAllFinalValues());
    }

    public static long toScaledLong(double value) {
        return Math.round(value * ATTRIBUTE_SCALE);
    }
}
