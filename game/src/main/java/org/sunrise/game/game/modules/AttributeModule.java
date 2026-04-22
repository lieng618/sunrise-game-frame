package org.sunrise.game.game.modules;

import com.alibaba.fastjson2.TypeReference;
import lombok.Getter;
import org.sunrise.game.game.annotation.HumanModule;
import org.sunrise.game.game.config.Enum.AttributeType;
import org.sunrise.game.game.logic.attribute.AttributeContainer;
import org.sunrise.game.game.logic.attribute.AttributeProvider;
import org.sunrise.game.genProto.gen.AttributeProto;
import org.sunrise.game.genProto.gen.TopicProto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@HumanModule
@Getter
public class AttributeModule extends BaseModule {
    private static final int SCALE = 1000;
    private final AttributeContainer container = new AttributeContainer();

    public AttributeModule(String humanId) {
        super(humanId);
    }

    @Override
    public void init() {
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
        container.setBaseValue(AttributeType.HP_PERCENT, 0);
        container.setBaseValue(AttributeType.MP_PERCENT, 0);
        container.setBaseValue(AttributeType.SPEED_PERCENT, 0);
    }

    @Override
    public void load() {
        getDbData("baseValues", new TypeReference<Map<Integer, Double>>() {
        }, value -> {
            if (value != null) {
                for (Map.Entry<Integer, Double> entry : value.entrySet()) {
                    container.setBaseValue(entry.getKey(), entry.getValue());
                }
            }
        });
    }

    @Override
    public void save() {
        putDbData("baseValues", container.getBaseValues());
    }

    @Override
    public void sendToClient() {
        recalculate();
        sendAttributeList();
    }

    @Override
    public void pulse() {
        if (container.isDirty()) {
            // 有模块触发属性变化时，重新计算属性，并通知变化值
            Map<Integer, Double> changeProviders = recalculate();
            notifyAttributeUpdate(changeProviders);
        }
    }

    /**
     * 重新计算一次属性
     * 收集所有模块的属性，获取变化的属性
     */
    public Map<Integer, Double> recalculate() {
        List<AttributeProvider> attributeProviders = new ArrayList<>();
        for (BaseModule baseModule : getHuman().getModules().values()) {
            if (baseModule.getAttribute() != null) {
                attributeProviders.add(baseModule.getAttribute());
            }
        }
        return container.resetProviders(attributeProviders);
    }

    public void markDirty() {
        container.markDirty();
    }

    public void notifyAttributeUpdate(Map<Integer, Double> changeProviders) {
        AttributeProto.MS2C_AttributeUpdate.Builder builder = AttributeProto.MS2C_AttributeUpdate.newBuilder();

        for (Map.Entry<Integer, Double> entry : changeProviders.entrySet()) {
            AttributeProto.STAttributeInfo info = AttributeProto.STAttributeInfo.newBuilder()
                    .setAttributeType(entry.getKey())
                    .setFinalValue(toScaledLong(entry.getValue()))
                    .build();
            builder.addAttributes(info);
        }

        getHuman().sendMsg(TopicProto.TOPIC.TOPIC_TYPE_ATTRIBUTE_VALUE,
                AttributeProto.FROM_SERVER.S2C_AttributeUpdate_VALUE, builder);
    }

    private void sendAttributeList() {
        Map<Integer, Double> finalValues = container.getAllFinalValues();
        AttributeProto.MS2C_AttributeList.Builder builder = AttributeProto.MS2C_AttributeList.newBuilder();

        for (Map.Entry<Integer, Double> entry : finalValues.entrySet()) {
            AttributeProto.STAttributeInfo info = AttributeProto.STAttributeInfo.newBuilder()
                    .setAttributeType(entry.getKey())
                    .setFinalValue(toScaledLong(entry.getValue()))
                    .build();
            builder.addAttributes(info);
        }

        getHuman().sendMsg(TopicProto.TOPIC.TOPIC_TYPE_ATTRIBUTE_VALUE,
                AttributeProto.FROM_SERVER.S2C_AttributeList_VALUE, builder);
    }

    private long toScaledLong(double value) {
        return Math.round(value * SCALE);
    }
}
