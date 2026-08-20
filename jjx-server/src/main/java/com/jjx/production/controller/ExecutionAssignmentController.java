package com.jjx.production.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.common.core.result.Result;
import com.jjx.production.domain.dto.AssignmentCreateDTO;
import com.jjx.production.domain.dto.AssignmentReleaseDTO;
import com.jjx.production.domain.vo.AssignmentViewVO;
import com.jjx.production.service.ExecutionAssignmentService;
import com.jjx.system.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 工序作业分配控制器（WP-B）
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/production/execution-assignment")
@Tag(name = "工序作业分配")
public class ExecutionAssignmentController {

    private final ExecutionAssignmentService assignmentService;

    @Operation(summary = "创建作业分配（一次多人，整批原子）")
    @SaCheckPermission("production:assignment:add")
    @PostMapping
    public Result<AssignmentViewVO> create(@RequestBody AssignmentCreateDTO dto) {
        return Result.success(assignmentService.create(dto,
                SecurityUtils.getUsername(), SecurityUtils.getUserId()));
    }

    @Operation(summary = "释放作业剩余（部分报工后剩余回到未分配池）")
    @SaCheckPermission("production:assignment:add")
    @PostMapping("/{id}/release")
    public Result<AssignmentViewVO> release(@PathVariable Long id,
                                            @RequestBody AssignmentReleaseDTO dto) {
        return Result.success(assignmentService.release(id, dto,
                SecurityUtils.getUsername(), SecurityUtils.getUserId()));
    }

    @Operation(summary = "按工序查询分配视图")
    @GetMapping("/execution/{executionId}")
    public Result<AssignmentViewVO> getByExecution(@PathVariable Long executionId) {
        return Result.success(assignmentService.getByExecutionId(executionId));
    }
}
