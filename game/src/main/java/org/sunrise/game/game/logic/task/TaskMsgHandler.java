package org.sunrise.game.game.logic.task;

import org.sunrise.game.game.annotation.MsgHandlerClass;
import org.sunrise.game.game.annotation.MsgHandlerMethod;
import org.sunrise.game.game.human.HumanObject;
import org.sunrise.game.game.modules.TaskModule;
import org.sunrise.game.genProto.gen.TaskProto;
import org.sunrise.game.genProto.gen.TopicProto;

@MsgHandlerClass(packetType = TopicProto.TOPIC.TOPIC_TYPE_TASK_VALUE)
public class TaskMsgHandler {
    
    /**
     * 获取任务列表（空消息）
     */
    @MsgHandlerMethod(packetId = TaskProto.FROM_CLIENT.C2S_GetTaskList_VALUE)
    public static void getTaskList(HumanObject humanObject) {
        TaskModule module = humanObject.getModule(TaskModule.class);
        module.sendTaskList();
    }
    
    /**
     * 接受任务
     */
    @MsgHandlerMethod(packetId = TaskProto.FROM_CLIENT.C2S_AcceptTask_VALUE)
    public static void acceptTask(HumanObject humanObject, TaskProto.MC2S_AcceptTask data) {
        TaskModule module = humanObject.getModule(TaskModule.class);
        module.acceptTask(data.getTaskId());
    }
    
    /**
     * 提交任务
     */
    @MsgHandlerMethod(packetId = TaskProto.FROM_CLIENT.C2S_SubmitTask_VALUE)
    public static void submitTask(HumanObject humanObject, TaskProto.MC2S_SubmitTask data) {
        TaskModule module = humanObject.getModule(TaskModule.class);
        module.submitTask(data.getTaskId());
    }
}

