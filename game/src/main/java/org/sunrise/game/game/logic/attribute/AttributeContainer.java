package org.sunrise.game.game.logic.attribute;

import lombok.Getter;
import org.sunrise.game.game.config.Enum.AttributeType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AttributeContainer {
    private static final Map<Integer, int[]> PERCENT_TO_TARGETS = new LinkedHashMap<>();

    static {
        PERCENT_TO_TARGETS.put(AttributeType.ATTACK_PERCENT, new int[]{AttributeType.ATTACK});
        PERCENT_TO_TARGETS.put(AttributeType.DEFENSE_PERCENT, new int[]{AttributeType.DEFENSE});
        PERCENT_TO_TARGETS.put(AttributeType.HP_PERCENT, new int[]{AttributeType.HP, AttributeType.MAX_HP});
        PERCENT_TO_TARGETS.put(AttributeType.MP_PERCENT, new int[]{AttributeType.MP, AttributeType.MAX_MP});
        PERCENT_TO_TARGETS.put(AttributeType.SPEED_PERCENT, new int[]{AttributeType.SPEED});
    }

    // 基础属性
    @Getter
    private final Map<Integer, Double> baseValues = new HashMap<>();
    // 附加属性
    private final List<AttributeProvider> providers = new ArrayList<>();
    // 最终计算的属性
    private final Map<Integer, Double> cachedFinalValues = new HashMap<>();
    @Getter
    private boolean dirty = true;

    public void setBaseValue(int attributeType, double value) {
        baseValues.put(attributeType, value);
        dirty = true;
    }

    public double getBaseValue(int attributeType) {
        return baseValues.getOrDefault(attributeType, 0.0);
    }

    public Map<Integer, Double> resetProviders(List<AttributeProvider> newProviders) {
        // 创建变化的属性
        Map<Integer, Double> changedAttributes = new HashMap<>();
        // 缓存旧属性
        Map<Integer, Double> oldFinalValues = new HashMap<>(cachedFinalValues);
        // 计算新属性
        providers.clear();
        providers.addAll(newProviders);
        recalculate();
        // 收集所有的属性id
        HashSet<Integer> allKeys = new HashSet<>(oldFinalValues.keySet());
        allKeys.addAll(cachedFinalValues.keySet());

        // 检查变化
        for (int type : allKeys) {
            double oldVal = oldFinalValues.getOrDefault(type, 0.0D);
            double newVal = cachedFinalValues.getOrDefault(type, 0.0D);

            // 只要不相等，就视为变化
            if (Double.doubleToLongBits(oldVal) != Double.doubleToLongBits(newVal)) {
                changedAttributes.put(type, newVal);
            }
        }
        return changedAttributes;
    }

    public double getFinalValue(int attributeType) {
        return cachedFinalValues.getOrDefault(attributeType, 0.0);
    }

    public Map<Integer, Double> getAllFinalValues() {
        return new HashMap<>(cachedFinalValues);
    }

    public void recalculate() {
        cachedFinalValues.clear();

        cachedFinalValues.putAll(baseValues);

        for (AttributeProvider provider : providers) {
            for (Map.Entry<Integer, Double> entry : provider.getExtraValues().entrySet()) {
                cachedFinalValues.merge(entry.getKey(), entry.getValue(), Double::sum);
            }
        }

        for (Map.Entry<Integer, int[]> entry : PERCENT_TO_TARGETS.entrySet()) {
            int percentType = entry.getKey();
            double percentBonus = cachedFinalValues.getOrDefault(percentType, 0.0);
            if (percentBonus != 0) {
                for (int targetType : entry.getValue()) {
                    double flatValue = cachedFinalValues.getOrDefault(targetType, 0.0);
                    cachedFinalValues.put(targetType, flatValue * (1 + percentBonus));
                }
            }
        }

        dirty = false;
    }

    public void markDirty() {
        dirty = true;
    }
}
