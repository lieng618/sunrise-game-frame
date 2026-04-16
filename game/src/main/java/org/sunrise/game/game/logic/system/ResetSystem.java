package org.sunrise.game.game.logic.system;

import com.alibaba.fastjson2.TypeReference;
import org.sunrise.game.game.human.HumanObject;
import org.sunrise.game.game.human.HumanObjectManger;
import org.sunrise.game.game.logic.ToolsUtils;
import org.sunrise.game.game.modules.BaseModule;
import org.sunrise.game.game.modules.DataModule;

import java.util.Map;

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
     * 检测跨天刷新
     */
    private void pulseHandlerDailyRefresh() {
        if (!isInitEnd()) {
            return;
        }
        long cur = System.currentTimeMillis();
        if (cur < nextDailyRefreshTime) {
            return;
        }
        for (HumanObject humanObject : HumanObjectManger.getHumanObjects()) {
            if (humanObject.getModule(DataModule.class).getLastDailyRefreshTime() < cur) {
                for (Map.Entry<String, BaseModule> entry : humanObject.getModules().entrySet()) {
                    entry.getValue().dailyReset();
                }
                humanObject.getModule(DataModule.class).setLastDailyRefreshTime(nextDailyRefreshTime);
            }
        }
        nextDailyRefreshTime = ToolsUtils.getDayTimeMillisByOffset(1);
    }

    /**
     * 检测跨周刷新
     */
    private void pulseHandlerWeekRefresh() {
        if (!isInitEnd()) {
            return;
        }
        long cur = System.currentTimeMillis();
        if (cur < nextWeekRefreshTime) {
            return;
        }
        for (HumanObject humanObject : HumanObjectManger.getHumanObjects()) {
            if (humanObject.getModule(DataModule.class).getLastDailyRefreshTime() < cur) {
                for (Map.Entry<String, BaseModule> entry : humanObject.getModules().entrySet()) {
                    entry.getValue().weekReset();
                }
                humanObject.getModule(DataModule.class).setLastWeekRefreshTime(nextWeekRefreshTime);
            }
        }
        nextWeekRefreshTime = ToolsUtils.getWeekZeroTimeMillisByOffset(7);
    }
}
