package com.jjx.production.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.common.core.result.Result;
import com.jjx.framework.common.controller.BaseController;
import com.jjx.production.domain.dto.TaskAssignItemDTO;
import com.jjx.production.domain.dto.TaskNodeQuantityDTO;
import com.jjx.production.domain.entity.ProductionTaskNode;
import com.jjx.production.domain.vo.MyTaskNodeVO;
import com.jjx.production.domain.vo.TaskCandidateVO;
import com.jjx.production.domain.vo.TaskNodeVO;
import com.jjx.production.domain.vo.TaskTreeEventVO;
import com.jjx.production.service.TaskNodeService;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.annotation.Log;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 生产任务树 Controller（P1 Task Tree Core）
 * 权限预留：production:task:view / assign / recall / return / admin（recall/return 待 P2 使用）
 */
@Tag(name = "生产任务树")
@RestController
@RequestMapping("/production/task-node")
@RequiredArgsConstructor
public class TaskNodeController extends BaseController {

    private final TaskNodeService taskNodeService;

    @Operation(summary = "工序执行任务树（第一次访问自动建立根节点）")
    @SaCheckPermission("production:task:view")
    @GetMapping("/execution/{executionId}/tree")
    public Result<TaskNodeVO> tree(@PathVariable Long executionId) {
        return Result.success(taskNodeService.getTaskTree(executionId));
    }

    @Operation(summary = "任务树懒加载：当前视角第一层任务节点（parentNodeId 为空）或指定节点的直接子节点；纯浏览不建根")
    @SaCheckPermission("production:task:view")
    @GetMapping("/execution/{executionId}/children")
    public Result<List<TaskNodeVO>> children(@PathVariable Long executionId,
                                             @RequestParam(required = false) Long parentNodeId) {
        return Result.success(taskNodeService.listChildren(executionId, parentNodeId));
    }

    @Operation(summary = "任务节点详情（单节点数量投影；视图范围校验；不加载子树）")
    @SaCheckPermission("production:task:view")
    @GetMapping("/detail/{taskNodeId}")
    public Result<TaskNodeVO> detail(@PathVariable Long taskNodeId) {
        return Result.success(taskNodeService.getNodeDetail(taskNodeId));
    }

    @Operation(summary = "任务树完整操作流水（分配/收回/退回/报工/撤销报工，按 executionId 聚合，时间升序）")
    @SaCheckPermission("production:task:view")
    @GetMapping("/execution/{executionId}/events")
    public Result<List<TaskTreeEventVO>> events(@PathVariable Long executionId) {
        return Result.success(taskNodeService.executionEvents(executionId));
    }

    @Operation(summary = "分配任务给下级（创建子任务节点；一次可多人，合计不得超过父节点可分配数量）")
    @SaCheckPermission("production:task:assign")
    @Log(module = "生产任务", businessType = BusinessType.INSERT, bizType = "'production_task'",
            bizId = "#result.data[0].executionId")
    @PostMapping("/{parentNodeId}/assign")
    public Result<List<ProductionTaskNode>> assign(@PathVariable Long parentNodeId,
                                                   @Valid @RequestBody List<TaskAssignItemDTO> items) {
        return Result.success(taskNodeService.assignChildren(parentNodeId, items));
    }

    @Operation(summary = "收回直接子节点部分剩余任务（P2：仅父节点持有人可收回；已完成/已下分数量不可收回）")
    @SaCheckPermission("production:task:recall")
    @Log(module = "生产任务", businessType = BusinessType.UPDATE, bizType = "'production_task'",
            bizId = "#result.data.executionId")
    @PostMapping("/{childNodeId}/recall")
    public Result<ProductionTaskNode> recall(@PathVariable Long childNodeId,
                                             @Valid @RequestBody TaskNodeQuantityDTO dto) {
        return Result.success(taskNodeService.recall(childNodeId, dto.getQuantity()));
    }

    @Operation(summary = "退回部分剩余任务给父节点（P2：仅节点本人可退回；root 不允许退回）")
    @SaCheckPermission("production:task:return")
    @Log(module = "生产任务", businessType = BusinessType.UPDATE, bizType = "'production_task'",
            bizId = "#result.data.executionId")
    @PostMapping("/{nodeId}/return")
    public Result<ProductionTaskNode> returnNode(@PathVariable Long nodeId,
                                                 @Valid @RequestBody TaskNodeQuantityDTO dto) {
        return Result.success(taskNodeService.returnNode(nodeId, dto.getQuantity()));
    }

    @Operation(summary = "我的任务节点（当前用户持有的 TaskNode + 执行上下文；数量动态汇总）")
    @SaCheckPermission("production:task:view")
    @GetMapping("/my")
    public Result<List<MyTaskNodeVO>> my() {
        return Result.success(taskNodeService.myTaskNodes());
    }

    @Operation(summary = "分配任务候选人员（当前用户部门子树内成员，最小可靠候选范围）")
    @SaCheckPermission("production:task:view")
    @GetMapping("/candidates")
    public Result<List<TaskCandidateVO>> candidates() {
        return Result.success(taskNodeService.candidates());
    }
}
