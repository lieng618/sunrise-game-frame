package org.sunrise.game.game.modules;

import com.alibaba.fastjson2.TypeReference;
import com.github.houbb.sensitive.word.core.SensitiveWordHelper;
import lombok.Getter;
import lombok.Setter;
import org.sunrise.game.game.annotation.HumanModule;
import org.sunrise.game.game.logic.ToolsUtils;
import org.sunrise.game.genProto.gen.HumanProto;
import org.sunrise.game.genProto.gen.TopicProto;
import org.sunrise.game.genRpc.gen.CallEnum;
import org.sunrise.game.rpc.function.RpcFunction;

@HumanModule
@Getter
public class DataModule extends BaseModule {
    private String name;  //角色名字
    private int level;  //角色等级
    private int exp;  //经验
    private String headIcon;  //角色头像
    private int fightPower;  //战斗力
    private int sex;  //性别
    @Setter
    private long lastDailyRefreshTime; //上次跨天刷新的时间
    @Setter
    private long lastWeekRefreshTime; //上次跨周刷新的时间

    public DataModule(String humanId) {
        super(humanId);
    }

    @Override
    public void init() {
        name = "Human";
        headIcon = "default";
        sex = 1;
        lastDailyRefreshTime = ToolsUtils.getTodayZeroTimeMillis();
        lastWeekRefreshTime = ToolsUtils.getWeekZeroTimeMillis();

        // 同步初始化到玩家简略信息系统
        syncToPlayerInfoSystem();
    }

    @Override
    public void load() {
        getDbData("name", new TypeReference<String>() {
        }, value -> name = value);
        getDbData("level", new TypeReference<Integer>() {
        }, value -> level = value);
        getDbData("exp", new TypeReference<Integer>() {
        }, value -> exp = value);
        getDbData("headIcon", new TypeReference<String>() {
        }, value -> headIcon = value);
        getDbData("fightPower", new TypeReference<Integer>() {
        }, value -> fightPower = value);
        getDbData("sex", new TypeReference<Integer>() {
        }, value -> sex = value);
        getDbData("ld", new TypeReference<Long>() {
        }, value -> lastDailyRefreshTime = value);
        getDbData("lw", new TypeReference<Long>() {
        }, value -> lastWeekRefreshTime = value);
    }

    @Override
    public void save() {
        putDbData("name", name);
        putDbData("level", level);
        putDbData("exp", exp);
        putDbData("headIcon", headIcon);
        putDbData("fightPower", fightPower);
        putDbData("sex", sex);
        putDbData("ld", lastDailyRefreshTime);
        putDbData("lw", lastWeekRefreshTime);
    }

    /**
     * 修改名字
     */
    public void changeName(String newName) {
        if (SensitiveWordHelper.contains(newName)) {
            getHuman().sendTips("包含屏蔽词");
            return;
        }
        this.name = newName;
        syncToPlayerInfoSystem();

        HumanProto.MS2C_ChangeName.Builder builder = HumanProto.MS2C_ChangeName.newBuilder();
        builder.setName(newName);
        getHuman().sendMsg(TopicProto.TOPIC.TOPIC_TYPE_HUMAN_VALUE,
                HumanProto.FROM_SERVER.S2C_ChangeName_VALUE, builder);
    }

    /**
     * 修改等级
     */
    public void changeLevel(int newLevel) {
        this.level = newLevel;
        syncToPlayerInfoSystem();

        HumanProto.MS2C_HumanInfoChange.Builder builder = HumanProto.MS2C_HumanInfoChange.newBuilder();
        builder.setLevel(newLevel);
        getHuman().sendMsg(TopicProto.TOPIC.TOPIC_TYPE_HUMAN_VALUE,
                HumanProto.FROM_SERVER.S2C_HumanInfoChange_VALUE, builder);
    }

    /**
     * 修改经验
     */
    public void changeExp(int newExp) {
        this.exp = newExp;
        syncToPlayerInfoSystem();

        HumanProto.MS2C_HumanInfoChange.Builder builder = HumanProto.MS2C_HumanInfoChange.newBuilder();
        builder.setExp(newExp);
        getHuman().sendMsg(TopicProto.TOPIC.TOPIC_TYPE_HUMAN_VALUE,
                HumanProto.FROM_SERVER.S2C_HumanInfoChange_VALUE, builder);
    }

    /**
     * 修改头像
     */
    public void changeHeadIcon(int headIconId) {
        this.headIcon = String.valueOf(headIconId);
        syncToPlayerInfoSystem();

        HumanProto.MS2C_ChangeHeadIcon.Builder builder = HumanProto.MS2C_ChangeHeadIcon.newBuilder();
        builder.setHeadIconId(headIconId);
        getHuman().sendMsg(TopicProto.TOPIC.TOPIC_TYPE_HUMAN_VALUE,
                HumanProto.FROM_SERVER.S2C_ChangeHeadIcon_VALUE, builder);
    }

    /**
     * 修改性别
     */
    public void changeSex(int newSex) {
        this.sex = newSex;
        syncToPlayerInfoSystem();

        HumanProto.MS2C_ChangeSex.Builder builder = HumanProto.MS2C_ChangeSex.newBuilder();
        builder.setSex(newSex);
        getHuman().sendMsg(TopicProto.TOPIC.TOPIC_TYPE_HUMAN_VALUE,
                HumanProto.FROM_SERVER.S2C_ChangeSex_VALUE, builder);
    }

    /**
     * 同步到玩家简略信息系统
     */
    private void syncToPlayerInfoSystem() {
        RpcFunction.newInstance()
                .call(CallEnum.GlobalPlayerInfoService_updatePlayerInfo,
                        "humanId", getHumanId(),
                        "name", name,
                        "level", level,
                        "headIcon", headIcon,
                        "sex", sex,
                        "fightPower", fightPower);
    }
}
