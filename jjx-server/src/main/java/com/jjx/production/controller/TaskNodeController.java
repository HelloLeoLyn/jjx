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
import com.jjx.production.service.TaskNodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

    @Operation(summary = "分配任务给下级（创建子任务节点；一次可多人，合计不得超过父节点可分配数量）")
    @SaCheckPermission("production:task:assign")
    @PostMapping("/{parentNodeId}/assign")
    public Result<List<ProductionTaskNode>> assign(@PathVariable Long parentNodeId,
                                                   @Valid @RequestBody List<TaskAssignItemDTO> items) {
        return Result.success(taskNodeService.assignChildren(parentNodeId, items));
    }

    @Operation(summary = "收回直接子节点部分剩余任务（P2：仅父节点持有人可收回；已完成/已下分数量不可收回）")
    @SaCheckPermission("production:task:recall")
    @PostMapping("/{childNodeId}/recall")
    public Result<ProductionTaskNode> recall(@PathVariable Long childNodeId,
                                             @Valid @RequestBody TaskNodeQuantityDTO dto) {
        return Result.success(taskNodeService.recall(childNodeId, dto.getQuantity()));
    }

    @Operation(summary = "退回部分剩余任务给父节点（P2：仅节点本人可退回；root 不允许退回）")
    @SaCheckPermission("production:task:return")
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
