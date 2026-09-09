package com.jjx.notification.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.jjx.common.core.result.Result;
import com.jjx.notification.domain.dto.NotifyTaskDTO;
import com.jjx.notification.service.NotifyTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通用催办端点（2026-09-09 dev-20260909-002）
 * 业务场景：移动端"请先启动工单"等 → 通知上级角色（product:all + 超管）+ 建待办。
 * 通用设计：任何业务要"按角色发通知 + 建待办任务"都可复用本端点（roleKeys/title/content 由调用方传入）。
 */
@Tag(name = "通用催办")
@RestController
@RequiredArgsConstructor
@RequestMapping("/common")
public class NotifyTaskController {

    private final NotifyTaskService notifyTaskService;

    @Operation(summary = "通用催办：按角色发通知 + 建待办任务")
    @SaCheckLogin
    @PostMapping("/notify-task")
    public Result<Void> notifyTask(@Validated @RequestBody NotifyTaskDTO dto) {
        notifyTaskService.notifyAndCreateTask(dto);
        return Result.success();
    }
}
