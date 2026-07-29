package com.jjx.kanban.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 看板任务实体（已迁移到 sys_task）
 * kanbanType → taskType, columnId → status
 */
@Data
@TableName("sys_task")
public class KanbanTask {
    @TableId(type = IdType.AUTO)
    private Long taskId;

    private String taskCode;

    private String title;

    private String description;

    @TableField("task_type")
    private String kanbanType;

    @TableField("status")
    private String columnId;

    private String sourceEvent;

    private Long sourceId;

    @TableField(exist = false)
    private String sourceNo;

    private Long assignRole;

    private String priority;

    private String status;

    @TableField("completed_time")
    private LocalDateTime completedAt;

    private String createBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    private String updateBy;

    @TableField(fill = FieldFill.UPDATE)
    private LocalDateTime updateTime;
}
