package com.jjx.kanban.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.common.core.result.Result;
import com.jjx.kanban.domain.entity.KanbanTask;
import com.jjx.kanban.service.KanbanTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 看板任务API — 供EventPublisher联动创建
 * 外部系统通过HTTP调用，在业务事件发生时创建看板卡片
 */
@Tag(name = "看板任务")
@RestController
@RequestMapping("/kanban/tasks")
@RequiredArgsConstructor
public class KanbanTaskController {

    private final KanbanTaskService kanbanTaskService;

    @Operation(summary = "创建看板任务")
    @PostMapping
    public Result<Long> create(@RequestBody KanbanTask task) {
        return Result.success(kanbanTaskService.createTask(task));
    }

    @Operation(summary = "更新看板任务")
    @PutMapping
    public Result<Boolean> update(@RequestBody KanbanTask task) {
        return Result.success(kanbanTaskService.updateTask(task));
    }

    @Operation(summary = "查询看板任务")
    @GetMapping("/{id}")
    public Result<KanbanTask> getById(@PathVariable Long id) {
        return Result.success(kanbanTaskService.getById(id));
    }
}
