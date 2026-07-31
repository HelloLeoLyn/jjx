package com.jjx.system.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("sys_task")
public class SysTask {
    @TableId(type = IdType.AUTO)
    private Long taskId;

    private String taskCode;

    private String taskType;

    /** 看板模块: office/emergency/production/dev */
    private String kanbanModule;

    private String title;

    private String description;

    private String bizType;

    private Long bizId;

    private Long assigneeId;

    private String assigneeName;

    private Long assignRole;

    private Integer status;

    private String priority;

    private String sourceEvent;

    private Long sourceId;

    private Long resultId;

    private String resultType;

    private LocalDateTime startTime;

    private LocalDate deadline;

    private LocalDateTime completedTime;

    private String createBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    private String updateBy;

    @TableField(fill = FieldFill.UPDATE)
    private LocalDateTime updateTime;

    private String remark;
}
