package org.sunrise.game.game.modules;

import com.alibaba.fastjson2.TypeReference;
import lombok.Getter;
import lombok.Setter;
import org.sunrise.game.game.annotation.HumanModule;
import org.sunrise.game.game.config.Enum.TaskStatus;
import org.sunrise.game.game.config.Tables;
import org.sunrise.game.game.config.task.TbTask;
import org.sunrise.game.game.logic.task.TaskData;
import org.sunrise.game.genProto.gen.TaskProto;
import org.sunrise.game.genProto.gen.TopicProto;

import java.util.HashMap;
import java.util.Map;

@HumanModule
@Getter
@Setter
public class TaskModule extends BaseModule {
    // 任务数据：taskId -> TaskData
    private Map<Integer, TaskData> tasks = new HashMap<>();

    public TaskModule(String humanId) {
        super(humanId);
    }

    @Override
    public void init() {
        // 新角色初始化
        for (Integer taskInitId : Tables.ConfigParam.getTaskInitIds()) {
            acceptTaskCore(taskInitId);
        }
    }

    @Override
    public void load() {
        // 从数据库加载任务数据
        getDbData("tasks", new TypeReference<Map<Integer, TaskData>>() {}, value -> {
            if (value != null) {
                this.tasks = value;
            }
        });
    }

    @Override
    public void save() {
        // 保存任务数据到数据库
        putDbData("tasks", tasks);
    }

    @Override
    public void sendToClient() {
        // 登录时发送任务列表给客户端
        sendTaskList();
    }

    /**
     * 发送任务列表
     */
    public void sendTaskList() {
        TaskProto.MS2C_GetTaskList.Builder builder = TaskProto.MS2C_GetTaskList.newBuilder();
        for (TaskData task : tasks.values()) {
            TaskProto.STTaskInfo taskInfo = TaskProto.STTaskInfo.newBuilder()
                .setTaskId(task.getTaskId())
                .setProgress(task.getProgress())
                .setStatus(task.getStatus())
                .build();
            builder.addTasks(taskInfo);
        }
        getHuman().sendMsg(TopicProto.TOPIC.TOPIC_TYPE_TASK_VALUE,
                          TaskProto.FROM_SERVER.S2C_GetTaskList_VALUE, builder);
    }

    public boolean acceptTaskCore(int taskId) {
        if (tasks.containsKey(taskId)) {
            return false; // 任务已存在
        }
        TaskData task = new TaskData(taskId, 0);
        tasks.put(taskId, task);
        return true;
    }

    /**
     * 接受任务
     */
    public void acceptTask(int taskId) {
        if (acceptTaskCore(taskId)) {
            // 通知客户端任务更新
            notifyTaskUpdate(taskId);
        }
    }

    /**
     * 提交任务
     */
    public void submitTask(int taskId) {
        TaskData taskData = tasks.get(taskId);
        if (taskData == null) return;
        
        // 实现任务提交逻辑
        // TODO: 发放任务奖励等
        
        // 更新任务状态为已提交
        taskData.setStatus(TaskStatus.SUBMIT);
        
        // 通知客户端任务更新
        notifyTaskUpdate(taskId);
        
        // 移除已完成的任务
        tasks.remove(taskId);

        // 接取新任务
        TbTask task = Tables.ConfigTask.get(taskId);
        if (task != null) {
            if (task.nextId > 0 && Tables.ConfigTask.get(task.nextId) != null) {
                acceptTask(task.nextId);
            }
        }
    }

    /**
     * 通知客户端任务更新
     */
    public void notifyTaskUpdate(int taskId) {
        TaskData task = tasks.get(taskId);
        if (task == null) return;
        
        TaskProto.MS2C_TaskUpdate.Builder builder = TaskProto.MS2C_TaskUpdate.newBuilder();
        TaskProto.STTaskInfo taskInfo = TaskProto.STTaskInfo.newBuilder()
            .setTaskId(taskId)
            .setProgress(task.getProgress())
            .setStatus(task.getStatus())
            .build();
        builder.setTask(taskInfo);
        getHuman().sendMsg(TopicProto.TOPIC.TOPIC_TYPE_TASK_VALUE, 
                          TaskProto.FROM_SERVER.S2C_TaskUpdate_VALUE, builder);
    }

}
