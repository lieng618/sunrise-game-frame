package org.sunrise.game.game.logic.task;

import org.sunrise.game.game.config.Enum.TaskStatus;
import org.sunrise.game.game.config.Enum.TaskType;
import org.sunrise.game.game.config.Tables;
import org.sunrise.game.game.config.task.TbTask;
import org.sunrise.game.game.human.HumanObject;
import org.sunrise.game.game.modules.TaskModule;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 任务事件管理器
 * 负责触发任务事件和更新任务进度
 */
public class TaskEventManager {
    
    /**
     * 触发任务事件
     * @param human 玩家对象
     * @param subtype 任务类型（TaskType枚举值）
     * @param params 事件参数（可变参数）
     */
    public static void triggerEvent(HumanObject human, int subtype, int addValue, int... params) {
        if (human == null) {
            return;
        }

        TaskModule taskModule = human.getModule(TaskModule.class);
        if (taskModule == null) {
            return;
        }

        // 将可变参数转换为List
        List<Integer> paramsList = new ArrayList<>();
        for (int param : params) {
            paramsList.add(param);
        }

        // 遍历玩家所有任务
        for (TaskData taskData : taskModule.getTasks().values()) {
            TbTask task = Tables.ConfigTask.get(taskData.getTaskId());
            if (task == null) {
                continue;
            }

            // 只处理指定类型的任务
            if (task.type != subtype) {
                continue;
            }

            // 已完成的无需检测
            if (taskData.getStatus() != TaskStatus.DOING) {
                continue;
            }

            // 参数数量必须匹配
            if (paramsList.size() != task.taskParams.size()) {
                continue;
            }

            // 检查参数是否匹配（根据任务类型有不同的匹配规则）
            if (!checkParamsMatch(subtype, paramsList, task.taskParams)) {
                continue;
            }

            // 更新进度
            taskData.addProgress(addValue);

            // 检查是否完成
            if (taskData.getProgress() >= task.goal) {
                taskData.setProgress(task.goal);
                taskData.setStatus(TaskStatus.FINISH);
            }

            // 通知客户端任务更新
            taskModule.notifyTaskUpdate(task.id);
        }
    }

    /**
     * 检查参数是否匹配
     * 默认情况下，所有参数均要相等
     * 特殊任务类型可以自定义匹配规则
     */
    private static boolean checkParamsMatch(int subtype, List<Integer> params, List<Integer> taskParams) {
        switch (subtype) {
            case TaskType.LEVEL_UP:
            case TaskType.EXP_ADD:
            case TaskType.ITEM_USE:
            case TaskType.ITEM_GET:
            case TaskType.KILL_MONSTER:
            default:
                // 默认情况下，所有参数均要相等
                for (int i = 0; i < params.size(); i++) {
                    if (!Objects.equals(params.get(i), taskParams.get(i))) {
                        return false;
                    }
                }
                return true;
        }
    }
}
