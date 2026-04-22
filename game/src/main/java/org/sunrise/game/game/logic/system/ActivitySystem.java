package org.sunrise.game.game.logic.system;

import org.sunrise.game.game.annotation.GameSystem;
import org.sunrise.game.game.config.Enum.ActivityType;
import org.sunrise.game.game.config.activity.TbActivity;
import org.sunrise.game.game.logic.activity.logic.BaseActivityLogic;
import org.sunrise.game.game.logic.activity.logic.CheckInActivityLogic;

/**
 * 活动系统
 */
@GameSystem
public class ActivitySystem extends BaseSystem {

    @SuppressWarnings("unchecked")
    public <T extends BaseActivityLogic> T getActivityLogic(TbActivity cfg, String humanId) {
        if (cfg.type == ActivityType.CheckIn) {
            return (T) new CheckInActivityLogic(cfg.id, humanId);
        } else {
            return (T) new BaseActivityLogic(cfg.id, humanId);
        }
    }
}

