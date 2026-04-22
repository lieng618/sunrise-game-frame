package org.sunrise.game.game.modules;

import com.alibaba.fastjson2.TypeReference;
import lombok.Getter;
import lombok.Setter;
import org.sunrise.game.game.annotation.HumanModule;
import org.sunrise.game.game.config.Enum.ActivityStatus;
import org.sunrise.game.game.config.Tables;
import org.sunrise.game.game.config.activity.TbActivity;
import org.sunrise.game.game.logic.ToolsUtils;
import org.sunrise.game.game.logic.activity.logic.ActivityDbData;
import org.sunrise.game.game.logic.activity.logic.BaseActivityLogic;
import org.sunrise.game.game.logic.system.ActivitySystem;
import org.sunrise.game.game.logic.system.GameSystemUtils;
import org.sunrise.game.genProto.gen.ActivityProto;
import org.sunrise.game.genProto.gen.TopicProto;

import java.util.HashMap;
import java.util.Map;

@HumanModule
@Getter
@Setter
public class ActivityModule extends BaseModule {

    private Map<Integer, BaseActivityLogic> activities = new HashMap<>();

    public ActivityModule(String humanId) {
        super(humanId);
    }

    @Override
    public void load() {
        initActivityList();
        for (Map.Entry<Integer, BaseActivityLogic> entry : activities.entrySet()) {
            getDbData(String.valueOf(entry.getKey()), new TypeReference<ActivityDbData>() {
            }, value -> {
                if (value != null) {
                    entry.getValue().setData(value);
                    entry.getValue().load();
                }
            });
        }
    }

    @Override
    public void save() {
        for (Map.Entry<Integer, BaseActivityLogic> entry : activities.entrySet()) {
            entry.getValue().save();
            putDbData(String.valueOf(entry.getKey()), entry.getValue().getData());
        }
    }

    @Override
    public void sendToClient() {
        initActivityList();
        pulseChangeActivityStatus();
        sendActivityList();
    }

    @Override
    public void pulsePerSec() {
        super.pulsePerSec();
        pulseChangeActivityStatus();
    }

    /**
     * 进行初始化
     */
    public void initActivityList() {
        if (Tables.ConfigActivity == null) return;
        ActivitySystem activitySystem = GameSystemUtils.getSystem(ActivitySystem.class);
        if (activitySystem == null) return;
        for (TbActivity cfg : Tables.ConfigActivity.getDataList()) {
            if (cfg == null) continue;
            activities.computeIfAbsent(cfg.id, r -> activitySystem.getActivityLogic(cfg, getHumanId()));
        }
    }

    public void pulseChangeActivityStatus() {
        long now = System.currentTimeMillis();
        for (BaseActivityLogic activityData : activities.values()) {
            TbActivity tbActivity = Tables.ConfigActivity.get(activityData.getData().getActivityId());
            if (tbActivity == null) {
                continue;
            }
            if (tbActivity.naturalTime == 1) {
                // 自然时间
                long beginMs = ToolsUtils.getTimeMillis(tbActivity.beginTime);
                long endMs = ToolsUtils.getTimeMillis(tbActivity.endTime);
                if (beginMs <= 0 || endMs <= 0 || endMs <= beginMs) {
                    continue;
                }
                int curStatus = activityData.getData().getStatus();

                // 切换为结束状态
                if (now >= endMs) {
                    if (curStatus == ActivityStatus.DOING) {
                        activityData.onEnd();
                        notifyActivityUpdate(activityData);
                    } else if (curStatus == ActivityStatus.NOT_OPEN) {
                        activityData.onEnd();
                        notifyActivityUpdate(activityData);
                    }
                    continue;
                }
                // 切换为开启状态
                if (now >= beginMs) {
                    if (curStatus == ActivityStatus.NOT_OPEN) {
                        activityData.onStart();
                        notifyActivityUpdate(activityData);
                    }
                }
            }
        }
    }

    /**
     * 登录时发送活动列表
     */
    public void sendActivityList() {
        ActivityProto.MS2C_GetActivityList.Builder builder = ActivityProto.MS2C_GetActivityList.newBuilder();
        for (BaseActivityLogic activityData : activities.values()) {
            TbActivity tbActivity = Tables.ConfigActivity.get(activityData.getData().getActivityId());
            if (tbActivity == null) {
                continue;
            }
            ActivityProto.STActivityStatusInfo.Builder oneBuilder = ActivityProto.STActivityStatusInfo.newBuilder()
                    .setId(activityData.getData().getActivityId())
                    .setStatus(activityData.getData().getStatus())
                    .setBeginTime(ToolsUtils.getTimeMillis(tbActivity.beginTime))
                    .setEndTime(ToolsUtils.getTimeMillis(tbActivity.endTime));
            builder.addActivities(oneBuilder.build());

        }
        getHuman().sendMsg(TopicProto.TOPIC.TOPIC_TYPE_ACTIVITY_VALUE,
                ActivityProto.FROM_SERVER.S2C_GetActivityList_VALUE, builder);
    }

    /**
     * 推送单个活动更新
     */
    public void notifyActivityUpdate(BaseActivityLogic activityData) {
        TbActivity tbActivity = Tables.ConfigActivity.get(activityData.getData().getActivityId());
        if (tbActivity == null) return;
        ActivityProto.MS2C_ActivityUpdate.Builder builder = ActivityProto.MS2C_ActivityUpdate.newBuilder();
        ActivityProto.STActivityStatusInfo.Builder oneBuilder = ActivityProto.STActivityStatusInfo.newBuilder()
                .setId(activityData.getData().getActivityId())
                .setStatus(activityData.getData().getStatus())
                .setBeginTime(ToolsUtils.getTimeMillis(tbActivity.beginTime))
                .setEndTime(ToolsUtils.getTimeMillis(tbActivity.endTime));
        builder.setActivity(oneBuilder.build());
        getHuman().sendMsg(TopicProto.TOPIC.TOPIC_TYPE_ACTIVITY_VALUE,
                ActivityProto.FROM_SERVER.S2C_ActivityUpdate_VALUE, builder);
    }

    public void onAction(ActivityProto.MC2S_ActivityAction data) {
        if (data == null) {
            return;
        }
        TbActivity tbActivity = Tables.ConfigActivity.get(data.getId());
        if (tbActivity == null) {
            return;
        }
        BaseActivityLogic activityLogic = activities.get(data.getId());
        if (activityLogic == null) {
            return;
        }
        if (activityLogic.getData().getStatus() != ActivityStatus.DOING) {
            return;
        }
        activityLogic.onAction(data.getActionId(), data.getData());
    }
}
