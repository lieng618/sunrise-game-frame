package org.sunrise.game.game.modules;

import com.alibaba.fastjson2.TypeReference;
import lombok.Getter;
import lombok.Setter;
import org.sunrise.game.game.config.Enum.AttributeType;
import org.sunrise.game.game.logic.attribute.AttributeProvider;
import org.sunrise.game.genProto.gen.MinerProto;
import org.sunrise.game.genProto.gen.TopicProto;

@Getter
@Setter
public class MinerModule extends BaseModule {
    private float rope_speed;    //绳子速度
    private int hook_count;    //钩子数量
    private float hook_power;    //钩子力度
    private int level1;    //绳子速度升级次数
    private int level2;    //钩子数量升级次数
    private int level3;    //钩子力度升级次数
    private int levelIndex; //当前关卡
    private int upgradePoints; //当前拥有点数
    private AttributeProvider attributeProvider = new AttributeProvider(); // 属性

    public MinerModule(String humanId) {
        super(humanId);
    }

    @Override
    public void init() {
        rope_speed = 6;
        hook_count = 1;
        hook_power = (float) 0.6;
        level1 = 0;
        level2 = 0;
        level3 = 0;
        levelIndex = 0;
        upgradePoints = 0;
        resetAttribute();
    }

    @Override
    public void sendToClient() {
        MinerProto.PlayerStats.Builder stats = MinerProto.PlayerStats.newBuilder().setRopeSpeed(rope_speed).setHookCount(hook_count).setHookPower(hook_power);
        MinerProto.UpgradeCounts.Builder levelCount = MinerProto.UpgradeCounts.newBuilder().setRopeSpeed(level1).setHookCount(level2).setHookPower(level3);
        getHuman().sendMsg(TopicProto.TOPIC.TOPIC_TYPE_MINER_VALUE, MinerProto.FROM_SERVER.S2C_SyncData_VALUE,
                MinerProto.MS2C_SyncData.newBuilder().setLevelIndex(levelIndex).setUpgradePoints(upgradePoints).setStats(stats).setUpgradeCounts(levelCount));
    }

    public void syncData(MinerProto.MC2S_SyncData data) {
        levelIndex = data.getLevelIndex();
        upgradePoints = data.getUpgradePoints();
        rope_speed = data.getStats().getRopeSpeed();
        hook_count = data.getStats().getHookCount();
        hook_power = data.getStats().getHookPower();
        level1 = data.getUpgradeCounts().getRopeSpeed();
        level2 = data.getUpgradeCounts().getHookCount();
        level3 = data.getUpgradeCounts().getHookPower();

        resetAttribute();
        getHuman().getModule(AttributeModule.class).markDirty();
    }

    // 假设此模块有属性
    // 重新计算此模块的属性
    public void resetAttribute() {
        attributeProvider.reset();
        attributeProvider.addValue(AttributeType.MAX_HP, levelIndex);
        attributeProvider.addValue(AttributeType.MAX_MP, upgradePoints);
    }

    @Override
    public AttributeProvider getAttribute() {
        return attributeProvider;
    }

    @Override
    public void load() {
        getDbData("rope_speed", new TypeReference<Float>() {}, value -> rope_speed = value);
        getDbData("hook_count", new TypeReference<Integer>() {}, value -> hook_count = value);
        getDbData("hook_power", new TypeReference<Float>() {}, value -> hook_power = value);
        getDbData("level1", new TypeReference<Integer>() {}, value -> level1 = value);
        getDbData("level2", new TypeReference<Integer>() {}, value -> level2 = value);
        getDbData("level3", new TypeReference<Integer>() {}, value -> level3 = value);
        getDbData("levelIndex", new TypeReference<Integer>() {}, value -> levelIndex = value);
        getDbData("upgradePoints", new TypeReference<Integer>() {}, value -> upgradePoints = value);
        resetAttribute();
    }

    @Override
    public void save() {
        putDbData("rope_speed", rope_speed);
        putDbData("hook_count", hook_count);
        putDbData("hook_power", hook_power);
        putDbData("level1", level1);
        putDbData("level2", level2);
        putDbData("level3", level3);
        putDbData("levelIndex", levelIndex);
        putDbData("upgradePoints", upgradePoints);
    }
}
