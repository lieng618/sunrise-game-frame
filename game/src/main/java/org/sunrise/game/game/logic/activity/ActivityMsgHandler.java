package org.sunrise.game.game.logic.activity;

import org.sunrise.game.game.annotation.MsgHandlerClass;
import org.sunrise.game.game.annotation.MsgHandlerMethod;
import org.sunrise.game.game.human.HumanObject;
import org.sunrise.game.game.modules.ActivityModule;
import org.sunrise.game.genProto.gen.ActivityProto;
import org.sunrise.game.genProto.gen.TopicProto;

@MsgHandlerClass(packetType = TopicProto.TOPIC.TOPIC_TYPE_ACTIVITY_VALUE)
public class ActivityMsgHandler {
    /**
     * 获取活动列表
     */
    @MsgHandlerMethod(packetId = ActivityProto.FROM_CLIENT.C2S_GetActivityList_VALUE)
    public static void getActivityList(HumanObject humanObject) {
        ActivityModule module = humanObject.getModule(ActivityModule.class);
        module.sendActivityList();
    }

    @MsgHandlerMethod(packetId = ActivityProto.FROM_CLIENT.C2S_ActivityAction_VALUE)
    public static void ActivityAction(HumanObject humanObject, ActivityProto.MC2S_ActivityAction data) {
        ActivityModule module = humanObject.getModule(ActivityModule.class);
        module.onAction(data);
    }
}

