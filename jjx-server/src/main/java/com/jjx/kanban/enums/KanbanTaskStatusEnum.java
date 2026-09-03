package com.jjx.kanban.enums;

import com.jjx.common.enums.BizStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 看板任务状态枚举（sys_task.status）
 */
@Getter
@AllArgsConstructor
public enum KanbanTaskStatusEnum implements BizStatusEnum {
    PENDING(0, "待开始"),
    IN_PROGRESS(1, "进行中"),
    PENDING_REVIEW(2, "待审核"),
    BLOCKED(3, "阻塞"),
    ABANDONED(4, "已废弃"),
    COMPLETED(10, "已完成");

    private final Integer value;
    private final String label;

    public static KanbanTaskStatusEnum getByValue(Integer value) {
        if (value == null) {
            return null;
        }
        for (KanbanTaskStatusEnum status : values()) {
            if (status.getValue().equals(value)) {
                return status;
            }
        }
        return null;
    }
}
