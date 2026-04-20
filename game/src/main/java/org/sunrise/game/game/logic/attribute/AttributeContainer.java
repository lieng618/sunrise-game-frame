package org.sunrise.game.game.logic.attribute;

import lombok.Getter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class AttributeContainer {
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

        dirty = false;
    }

    public void markDirty() {
        dirty = true;
    }
}
