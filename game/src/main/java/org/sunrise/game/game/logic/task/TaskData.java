package org.sunrise.game.game.logic.task;

import lombok.Getter;
import lombok.Setter;
import org.sunrise.game.game.config.Enum.TaskStatus;

@Getter
@Setter
public class TaskData {
    private final int taskId;  // 任务ID
    private int progress;      // 进度
    private int status;        // 状态（TaskStatus枚举值）

    public TaskData(int taskId, int progress) {
        this.taskId = taskId;
        this.progress = progress;
        this.status = TaskStatus.DOING;
    }

    /**
     * 增加进度
     */
    public void addProgress(int amount) {
        this.progress += amount;
    }
}

