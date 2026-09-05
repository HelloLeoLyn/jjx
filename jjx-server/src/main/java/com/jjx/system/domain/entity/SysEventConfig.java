package com.jjx.system.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_event_config")
public class SysEventConfig {
    @TableId(value = "id", type = IdType.AUTO)
    private Long eventId;
    private String eventCode;
    private String eventName;
    private String bizModule;
    /** notification / task / both */
    private String eventType;
    /** 看板模块: office/emergency/production/dev */
    private String kanbanModule;
    /** 任务优先级: urgent/high/normal/low */
    private String priority;
    private Integer isEnabled;
    /** 角色ID列表 JSON: [7, 8] */
    private String targetRole;
    /** 通知标题/任务标题 */
    private String title;
    /** 通知内容/任务描述 */
    private String content;
    /** 排除触发者（任务侧） */
    private Integer excludeTrigger;
    /** 触发时关闭的source_event列表，逗号分隔 */
    private String closeSourceEvents;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
