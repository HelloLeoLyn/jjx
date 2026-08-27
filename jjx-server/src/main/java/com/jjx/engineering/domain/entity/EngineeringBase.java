package com.jjx.engineering.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 工程管理基础实体（已迁移到 sys_task）
 */
@Data
@TableName("sys_task")
public class EngineeringBase {
    @TableId(value = "task_id", type = IdType.AUTO)
    private Long id;

    @TableField("task_code")
    private String code;

    @TableField("title")
    private String name;

    private String status;

    private String remark;

    /** 工程基础数据标识（固定值） */
    @TableField(exist = false)
    private final String taskType = "engineering_base";

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;
}
