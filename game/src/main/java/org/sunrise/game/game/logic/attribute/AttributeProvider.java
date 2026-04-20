package org.sunrise.game.game.logic.attribute;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 附加属性
 */
@Data
public class AttributeProvider {
    private final Map<Integer, Double> extraValues = new HashMap<>();

    public void reset() {
        extraValues.clear();
    }

    public void setValue(int attributeType, double value) {
        extraValues.put(attributeType, value);
    }

    public void addValue(int attributeType, double value) {
        double old = extraValues.getOrDefault(attributeType, 0.0);
        extraValues.put(attributeType, value + old);
    }
}
