package org.sunrise.game.game.logic.system;

import com.alibaba.fastjson2.TypeReference;
import org.sunrise.game.game.annotation.GameSystem;
import org.sunrise.game.game.human.HumanObject;
import org.sunrise.game.game.human.HumanObjectManager;
import org.sunrise.game.game.logic.ToolsUtils;
import org.sunrise.game.game.modules.BaseModule;
import org.sunrise.game.game.modules.DataModule;

import java.util.Map;

@GameSystem
public class ResetSystem extends BaseSystem {
    private long nextDailyRefreshTime;
    private long nextWeekRefreshTime;

    @Override
    public void init() {
        nextDailyRefreshTime = ToolsUtils.getDayTimeMillisByOffset(1);
        nextWeekRefreshTime = ToolsUtils.getWeekZeroTimeMillisByOffset(7);
    }

    @Override
    public void load() {
        getDbData("dr", new TypeReference<Long>() {
        }, value -> nextDailyRefreshTime = value);
        getDbData("wr", new TypeReference<Long>() {
        }, value -> nextWeekRefreshTime = value);
    }

    @Override
    public void save() {
        putDbData("dr", nextDailyRefreshTime);
        putDbData("wr", nextWeekRefreshTime);
    }

    @Override
    public void pulsePerSec() {
        pulseHandlerDailyRefresh();
        pulseHandlerWeekRefresh();
    }

    /**
     * 玩家数据就绪后检查跨周/跨天刷新（登录加载、重连时调用，补偿离线期间未执行的刷新）
     */
    public void checkAndRefreshHuman(HumanObject humanObject) {
        if (!isInitEnd()) {
            return;
        }
        tryRefreshWeek(humanObject);
        tryRefreshDaily(humanObject);
    }

    /**
     * 检测跨天刷新（仅处理当前在线玩家；离线玩家登录时由 {@link #checkAndRefreshHuman} 补偿）
     */
    private void pulseHandlerDailyRefresh() {
        long cur = System.currentTimeMillis();
        if (cur < nextDailyRefreshTime) {
            return;
        }
        for (HumanObject humanObject : HumanObjectManager.getHumanObjects()) {
            tryRefreshDaily(humanObject);
        }
        nextDailyRefreshTime = ToolsUtils.getDayTimeMillisByOffset(1);
    }

    /**
     * 检测跨周刷新（仅处理当前在线玩家；离线玩家登录时由 {@link #checkAndRefreshHuman} 补偿）
     */
    private void pulseHandlerWeekRefresh() {
        long cur = System.currentTimeMillis();
        if (cur < nextWeekRefreshTime) {
            return;
        }
        for (HumanObject humanObject : HumanObjectManager.getHumanObjects()) {
            tryRefreshWeek(humanObject);
        }
        nextWeekRefreshTime = ToolsUtils.getWeekZeroTimeMillisByOffset(7);
    }

    private void tryRefreshDaily(HumanObject humanObject) {
        DataModule dataModule = humanObject.getModule(DataModule.class);
        if (dataModule.getLastDailyRefreshTime() >= nextDailyRefreshTime) {
            return;
        }
        for (Map.Entry<String, BaseModule> entry : humanObject.getModules().entrySet()) {
            entry.getValue().dailyReset();
        }
        dataModule.setLastDailyRefreshTime(nextDailyRefreshTime);
    }

    private void tryRefreshWeek(HumanObject humanObject) {
        DataModule dataModule = humanObject.getModule(DataModule.class);
        if (dataModule.getLastWeekRefreshTime() >= nextWeekRefreshTime) {
            return;
        }
        for (Map.Entry<String, BaseModule> entry : humanObject.getModules().entrySet()) {
            entry.getValue().weekReset();
        }
        dataModule.setLastWeekRefreshTime(nextWeekRefreshTime);
    }
}
