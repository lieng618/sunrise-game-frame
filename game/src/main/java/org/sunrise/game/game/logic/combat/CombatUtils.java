package org.sunrise.game.game.logic.combat;

import org.sunrise.game.game.config.Enum.AttributeType;
import org.sunrise.game.game.logic.attribute.AttributeContainer;
import org.sunrise.game.game.logic.unit.GameUnit;
import org.sunrise.game.game.logic.unit.Position;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class CombatUtils {

    /** 近战攻击距离 */
    public static final float ATTACK_RANGE = 150f;

    private CombatUtils() {
    }

    public static boolean isInAttackRange(GameUnit attacker, GameUnit defender) {
//        Position a = attacker.getPosition();
//        Position d = defender.getPosition();
//        float dx = a.getX() - d.getX();
//        float dy = a.getY() - d.getY();
//        float dz = a.getZ() - d.getZ();
//        float rangeSq = ATTACK_RANGE * ATTACK_RANGE;
//        return dx * dx + dy * dy + dz * dz <= rangeSq;
        return true;
    }

    /**
     * 计算对目标造成的伤害，闪避返回 0。
     */
    public static long calculateDamage(GameUnit attacker, GameUnit defender) {
        AttributeContainer atk = attacker.getAttributeContainer();
        AttributeContainer def = defender.getAttributeContainer();
        ensureRecalculated(atk);
        ensureRecalculated(def);

        double dodge = def.getFinalValue(AttributeType.DODGE);
        if (ThreadLocalRandom.current().nextDouble() < dodge) {
            return 0;
        }

        double attack = atk.getFinalValue(AttributeType.ATTACK);
        double defense = def.getFinalValue(AttributeType.DEFENSE);
        double damage = Math.max(1, attack - defense);

        double critRate = atk.getFinalValue(AttributeType.CRIT_RATE);
        if (ThreadLocalRandom.current().nextDouble() < critRate) {
            damage *= atk.getFinalValue(AttributeType.CRIT_DAMAGE);
        }
        return Math.max(0, Math.round(damage));
    }

    /**
     * 扣减目标生命值，返回变更的属性（当前仅 HP）。
     */
    public static Map<Integer, Double> applyDamage(GameUnit defender, long damage) {
        Map<Integer, Double> changed = new HashMap<>();
        if (damage <= 0) {
            return changed;
        }
        AttributeContainer container = defender.getAttributeContainer();
        ensureRecalculated(container);
        double hp = container.getFinalValue(AttributeType.HP);
        double newHp = Math.max(0, hp - damage);
        container.setBaseValue(AttributeType.HP, newHp);
        container.recalculate();
        changed.put(AttributeType.HP, newHp);
        return changed;
    }

    private static void ensureRecalculated(AttributeContainer container) {
        if (container != null && container.isDirty()) {
            container.recalculate();
        }
    }
}
