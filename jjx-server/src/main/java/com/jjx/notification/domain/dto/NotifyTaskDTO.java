package com.jjx.notification.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 通用催办 DTO（2026-09-09 dev-20260909-002）
 * 一次调用 = 按角色发站内信（sys_notification，每人一条）+ 建一条待办任务（sys_task，assign_role=首个角色）。
 * 以后其他业务要"通知某人/某角色 + 建待办"时直接复用 /common/notify-task。
 */
@Data
@Schema(description = "通用催办：按角色发通知+建待办任务")
public class NotifyTaskDTO {

    @Schema(description = "通知/任务标题")
    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题最长 200 字")
    private String title;

    @Schema(description = "通知正文/任务描述")
    @Size(max = 2000, message = "内容最长 2000 字")
    private String content;

    @Schema(description = "目标角色 role_key 列表（如 production:all / admin），通知发给这些角色的全部成员，任务 assign 给第一个角色；为空时取系统配置 notify_task_default_roles")
    private List<String> roleKeys;

    @Schema(description = "业务类型（如 production_order），用于通知/任务溯源")
    private String bizType;

    @Schema(description = "业务单据 ID")
    private Long bizId;

    @Schema(description = "优先级：normal/low/medium/high/urgent，默认 urgent")
    private String priority;

    @Schema(description = "待办任务看板模块，默认 office")
    private String kanbanModule;
}
