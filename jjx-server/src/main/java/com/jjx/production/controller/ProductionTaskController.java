package com.jjx.production.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jjx.common.core.result.Result;
import com.jjx.production.domain.dto.TaskAssignDTO;
import com.jjx.production.domain.dto.TaskCompleteDTO;
import com.jjx.production.domain.dto.TaskRecallDTO;
import com.jjx.production.domain.dto.TaskReturnDTO;
import com.jjx.production.domain.dto.TaskTreeQueryDTO;
import com.jjx.production.domain.dto.MyProductionExecutionQueryDTO;
import com.jjx.production.domain.vo.TaskCandidateVO;
import com.jjx.production.domain.vo.TaskCompletionDetailVO;
import com.jjx.production.domain.vo.TaskEventVO;
import com.jjx.production.domain.vo.TaskTreeRowVO;
import com.jjx.production.domain.vo.MyProductionExecutionVO;
import com.jjx.production.domain.vo.ChildProcessingDetailVO;
import com.jjx.production.service.ProductionTaskService;
import com.jjx.system.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 生产任务控制器（统一任务责任树；P1 Foundation + P2 Task Flow）
 * <p>
 * 永远只有一种 Task：第一层 = parent_task_id IS NULL 的真实记录；
 * children = parent_task_id = taskId 的直接子层（真懒加载，禁止整树加载）。
 * <p>
 * P2/P5 写动作：assign / recall / return / complete
 * - 锁顺序统一 parent → child；TaskEvent 与 Task 修改同事务
 * - 第一层 return 禁止
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/production/tasks")
@Tag(name = "生产任务管理")
public class ProductionTaskController {

    private final ProductionTaskService productionTaskService;

    @Operation(summary = "任务分页查询接口")
    @GetMapping("/page")
    @SaCheckPermission("production:task:view")
    public Result<Page<TaskTreeRowVO>> pageAccessibleTasks(TaskTreeQueryDTO queryDTO) {
        return Result.success(productionTaskService.pageAccessibleTasks(queryDTO));
    }

    @Operation(summary = "我的生产任务（本人有效Task起查，按execution聚合）")
    @GetMapping("/my-executions")
    @SaCheckPermission("production:task:view")
    public Result<Page<MyProductionExecutionVO>> myExecutions(MyProductionExecutionQueryDTO queryDTO) {
        return Result.success(productionTaskService.pageMyProductionExecutions(queryDTO));
    }

    @Operation(summary = "我的生产任务直接Child处理明细")
    @GetMapping("/my-executions/{executionId}/children")
    @SaCheckPermission("production:task:view")
    public Result<ChildProcessingDetailVO> myChildProcessingDetail(@PathVariable Long executionId) {
        return Result.success(productionTaskService.getMyChildProcessingDetail(executionId));
    }

    @Operation(summary = "当前用户是否拥有全部工序视角")
    @GetMapping("/execution-scope")
    @SaCheckPermission("production:operation-execution:view")
    public Result<Map<String, Boolean>> executionScope() {
        return Result.success(Map.of("global", SecurityUtils.isGlobalProductionScope()));
    }

    @Operation(summary = "任务详情")
    @GetMapping("/{taskId}")
    @SaCheckPermission("production:task:view")
    public Result<TaskTreeRowVO> detail(@PathVariable Long taskId) {
        return Result.success(productionTaskService.getDetail(taskId));
    }

    @Operation(summary = "按工序执行查询 First Task 责任与数量投影")
    @GetMapping("/execution/{executionId}/root")
    @SaCheckPermission("production:task:view")
    public Result<TaskTreeRowVO> rootByExecution(@PathVariable Long executionId) {
        return Result.success(productionTaskService.getFirstTaskByExecution(executionId));
    }

    @Operation(summary = "完成明细（当前 Task 有效子树内全部 APPROVED WorkReport；合计 = completedQuantity）")
    @GetMapping("/{taskId}/completion-details")
    @SaCheckPermission("production:task:view")
    public Result<List<TaskCompletionDetailVO>> completionDetails(@PathVariable Long taskId) {
        return Result.success(productionTaskService.listCompletionDetails(taskId));
    }

    @Operation(summary = "任务流转流水（task_id 或 related_task_id = 当前任务；按时间倒序）")
    @GetMapping("/{taskId}/events")
    @SaCheckPermission("production:task:view")
    public Result<List<TaskEventVO>> events(@PathVariable Long taskId) {
        return Result.success(productionTaskService.listEvents(taskId));
    }

    @Operation(summary = "我的任务（assignee_id = 当前登录人；可空 executionId 收窄；报工入口解析 taskId）")
    @GetMapping("/mine")
    @SaCheckPermission("production:work-report:add")
    public Result<List<TaskTreeRowVO>> mine(@RequestParam(required = false) Long executionId) {
        return Result.success(productionTaskService.listMyTasks(executionId));
    }

    @Operation(summary = "直接子任务（懒加载：每次只查 parent_task_id = taskId 的一层，排除 CANCELLED）")
    @GetMapping("/{taskId}/children")
    @SaCheckPermission("production:task:view")
    public Result<List<TaskTreeRowVO>> children(@PathVariable Long taskId) {
        return Result.success(productionTaskService.listChildren(taskId));
    }

    @Operation(summary = "可分配候选人员")
    @GetMapping("/{taskId}/candidates")
    @SaCheckPermission("production:task:view")
    public Result<List<TaskCandidateVO>> candidates(@PathVariable Long taskId) {
        return Result.success(productionTaskService.listCandidates(taskId));
    }

    @Operation(summary = "任务分配（每个层级同构：一次事务创建全部 Child，可多人、逐人拆量，合计不超过剩余）")
    @PostMapping("/{taskId}/assign")
    @SaCheckPermission("production:task:assign")
    public Result<Void> assign(@PathVariable Long taskId, @Valid @RequestBody TaskAssignDTO dto) {
        productionTaskService.assign(taskId, dto);
        return Result.success();
    }

    @Operation(summary = "收回（父执行人从直接 Child 拿回未完成/未再下发的量；禁止跨树/越级）")
    @PostMapping("/{parentTaskId}/recall")
    @SaCheckPermission("production:task:recall")
    public Result<Void> recall(@PathVariable Long parentTaskId, @Valid @RequestBody TaskRecallDTO dto) {
        productionTaskService.recall(parentTaskId, dto);
        return Result.success();
    }

    @Operation(summary = "退回（当前执行人把自身剩余退给父任务；第一层禁止）")
    @PostMapping("/{taskId}/return")
    @SaCheckPermission("production:task:return")
    public Result<Void> returnTask(@PathVariable Long taskId, @Valid @RequestBody TaskReturnDTO dto) {
        productionTaskService.returnTask(taskId, dto);
        return Result.success();
    }

    @Operation(summary = "人工确认完成（P5：自底向上确认链；完成后禁止 assign/recall/return/report）")
    @PostMapping("/{taskId}/complete")
    @SaCheckPermission("production:task:assign")
    public Result<Void> complete(@PathVariable Long taskId,
                                 @RequestBody(required = false) TaskCompleteDTO dto) {
        productionTaskService.complete(taskId, dto);
        return Result.success();
    }
}
