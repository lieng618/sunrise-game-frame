package org.sunrise.game.game.logic.system;

import org.sunrise.game.game.config.Enum.ActivityType;
import org.sunrise.game.game.config.activity.TbActivity;
import org.sunrise.game.game.human.HumanObject;
import org.sunrise.game.game.human.HumanObjectManger;
import org.sunrise.game.game.logic.activity.logic.BaseActivityLogic;
import org.sunrise.game.game.logic.activity.logic.CheckInActivityLogic;
import org.sunrise.game.game.modules.ActivityModule;

/**
 * 活动系统
 */
public class ActivitySystem extends BaseSystem {

    @Override
    public void pulse() {
        for (HumanObject humanObject : HumanObjectManger.getHumanObjects()) {
            ActivityModule module = humanObject.getModule(ActivityModule.class);
            if (module == null) continue;

            module.pulseChangeActivityStatus();
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends BaseActivityLogic> T getActivityLogic(TbActivity cfg, String humanId) {
        if (cfg.type == ActivityType.CheckIn) {
            return (T) new CheckInActivityLogic(cfg.id, humanId);
        } else {
            return (T) new BaseActivityLogic(cfg.id, humanId);
        }
    }
}

