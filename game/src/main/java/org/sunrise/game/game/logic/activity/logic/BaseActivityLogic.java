package org.sunrise.game.game.logic.activity.logic;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import lombok.Data;
import org.sunrise.game.game.config.Enum.ActivityStatus;
import org.sunrise.game.game.human.HumanObject;
import org.sunrise.game.game.human.HumanObjectManger;
import org.sunrise.game.genProto.gen.ActivityProto;
import org.sunrise.game.genProto.gen.TopicProto;

import java.util.function.Consumer;

/**
 * 活动逻辑基类
 */
@Data
public class BaseActivityLogic {

    private String humanId;
    private ActivityDbData data = new ActivityDbData();

    public BaseActivityLogic(int activityId, String humanId) {
        this.data.setActivityId(activityId);
        this.data.setStatus(ActivityStatus.NOT_OPEN);
        this.humanId = humanId;
    }

    /**
     * 单个活动读取数据
     */
    public void load() {

    }

    /**
     * 单个活动存储数据
     */
    public void save() {

    }

    public <T> void getDbData(String key, TypeReference<T> typeReference, Consumer<T> func) {
        String value = data.getDataMap().get(key);
        if (value == null) {
            return;
        }
        func.accept(JSON.parseObject(value, typeReference));
    }

    public void putDbData(String key, Object value) {
        data.getDataMap().put(key, JSON.toJSONString(value));
    }

    public HumanObject getHuman() {
        return HumanObjectManger.getHumanObject(humanId);
    }

    /**
     * 活动开启执行一次
     */
    public void onStart() {
        this.data.setStatus(ActivityStatus.DOING);
    }

    /**
     * 活动结束执行一次
     */
    public void onEnd() {
        this.data.setStatus(ActivityStatus.END);
    }

    /**
     * 活动行为处理
     */
    public void onAction(int actionId, ByteString data) {

    }

    public void sendToClient(int actionId, Message.Builder actionData) {
        ActivityProto.MS2C_ActivityAction.Builder builder = ActivityProto.MS2C_ActivityAction.newBuilder();
        builder.setId(getData().getActivityId()).setActionId(actionId).setData(actionData.build().toByteString());
        getHuman().sendMsg(TopicProto.TOPIC.TOPIC_TYPE_ACTIVITY_VALUE,
                ActivityProto.FROM_SERVER.S2S_ActivityAction_VALUE, builder);
    }
}

