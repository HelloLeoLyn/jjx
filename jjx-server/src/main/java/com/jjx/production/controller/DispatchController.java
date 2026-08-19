package com.jjx.production.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.common.core.page.PageResult;
import com.jjx.common.core.result.Result;
import com.jjx.framework.common.controller.BaseController;
import com.jjx.production.domain.dto.DispatchAssignDTO;
import com.jjx.production.domain.dto.DispatchAssignV1DTO;
import com.jjx.production.domain.dto.DispatchDelegateDTO;
import com.jjx.production.domain.dto.DispatchQueryDTO;
import com.jjx.production.domain.dto.DispatchReassignDTO;
import com.jjx.production.domain.dto.DispatchReturnDTO;
import com.jjx.production.domain.entity.ProductionDispatchLog;
import com.jjx.production.domain.vo.DispatchNodeComparisonVO;
import com.jjx.production.domain.vo.DispatchNodeVO;
import com.jjx.production.domain.vo.DispatchVO;
import com.jjx.production.service.DispatchService;
import com.jjx.system.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 生产派工 Controller
 */
@Tag(name = "生产派工")
@RestController
@RequestMapping("/production/dispatch")
@RequiredArgsConstructor
public class DispatchController extends BaseController {

    private final DispatchService dispatchService;

    @Operation(summary = "分页查询派工单")
    @SaCheckPermission("production:dispatch:list")
    @GetMapping("/page")
    public Result<PageResult<DispatchVO>> page(DispatchQueryDTO query) {
        return Result.success(dispatchService.page(query));
    }

    @Operation(summary = "工单全部派工单")
    @SaCheckPermission("production:dispatch:list")
    @GetMapping("/order/{orderId}")
    public Result<List<DispatchVO>> listByOrder(@PathVariable Long orderId) {
        return Result.success(dispatchService.listByOrder(orderId));
    }

    @Operation(summary = "工单待派工序（未派工/已退回）")
    @SaCheckPermission("production:dispatch:list")
    @GetMapping("/order/{orderId}/pending")
    public Result<List<DispatchVO>> pending(@PathVariable Long orderId) {
        return Result.success(dispatchService.listPending(orderId));
    }

    @Operation(summary = "某人的手下（负责部门+下级部门成员，转派候选）")
    @SaCheckPermission("production:dispatch:list")
    @GetMapping("/underlings/{userId}")
    public Result<List<com.jjx.system.domain.vo.SysUserVO>> underlings(@PathVariable Long userId) {
        return Result.success(dispatchService.underlings(userId));
    }

    @Operation(summary = "责任班组可选执行人（该部门及下级部门成员）")
    @SaCheckPermission("production:dispatch:list")
    @GetMapping("/team-persons/{teamId}")
    public Result<List<com.jjx.system.domain.vo.SysUserVO>> teamPersons(@PathVariable Long teamId) {
        return Result.success(dispatchService.teamPersons(teamId));
    }

    @Operation(summary = "当前用户可派工？（超管/生产负责人/被派工过）")
    @SaCheckPermission("production:dispatch:list")
    @GetMapping("/can-assign")
    public Result<Boolean> canAssign() {
        return Result.success(dispatchService.canAssign(SecurityUtils.getUserId()));
    }

    @Operation(summary = "执行人候选（自己+手下，按部门树组织）")
    @SaCheckPermission("production:dispatch:list")
    @GetMapping("/my-persons")
    public Result<List<com.jjx.system.domain.vo.SysUserVO>> myPersons() {
        return Result.success(dispatchService.myPersons());
    }

    @Operation(summary = "当前用户可管辖部门树（负责部门+下级，超管全量；责任班组可选范围）")
    @SaCheckPermission("production:dispatch:list")
    @GetMapping("/my-depts")
    public Result<List<com.jjx.system.domain.vo.DeptVO>> myDepts() {
        return Result.success(dispatchService.myDeptTree());
    }

    @Operation(summary = "派工单详情")
    @SaCheckPermission("production:dispatch:list")
    @GetMapping("/{id}")
    public Result<DispatchVO> getById(@PathVariable Long id) {
        return Result.success(dispatchService.getById(id));
    }

    @Operation(summary = "派工流水")
    @SaCheckPermission("production:dispatch:list")
    @GetMapping("/{id}/logs")
    public Result<List<ProductionDispatchLog>> logs(@PathVariable Long id) {
        return Result.success(dispatchService.logs(id));
    }

    // ==================== P1-B 只读：Node-first responsibility chain ====================

    @Operation(summary = "责任链历史（Node-first；无 Node 时 legacy fallback 兼容展示）")
    @SaCheckPermission("production:dispatch:list")
    @GetMapping("/{id}/nodes")
    public Result<List<DispatchNodeVO>> nodes(@PathVariable Long id) {
        return Result.success(dispatchService.nodes(id));
    }

    @Operation(summary = "当前 ACTIVE 责任节点（Node-first；无 Node 时 legacy 末位 operator）")
    @SaCheckPermission("production:dispatch:list")
    @GetMapping("/{id}/current-node")
    public Result<DispatchNodeVO> currentNode(@PathVariable Long id) {
        return Result.success(dispatchService.currentNode(id));
    }

    @Operation(summary = "Node vs legacy 一致性诊断（P1-E cutover 工具）", hidden = true)
    @SaCheckPermission("production:dispatch:list")
    @GetMapping("/{id}/compare-node-legacy")
    public Result<DispatchNodeComparisonVO> compareNodeLegacy(@PathVariable Long id) {
        return Result.success(dispatchService.compareNodeAndLegacy(id));
    }

    // ==================== P1-C 动作（Node 化写入） ====================

    @Operation(summary = "下派（DELEGATE：当前责任人向下派工）")
    @SaCheckPermission("production:dispatch:assign")
    @PostMapping("/{id}/delegate")
    public Result<DispatchVO> delegate(@PathVariable Long id, @Valid @RequestBody DispatchDelegateDTO dto) {
        return Result.success(dispatchService.delegate(id, dto.getTargetUserId(), dto.getRemark(),
                SecurityUtils.getUsername(), SecurityUtils.getUserId()));
    }

    @Operation(summary = "改派（REASSIGN：同级换人，历史不可覆盖）")
    @SaCheckPermission("production:dispatch:assign")
    @PostMapping("/{id}/reassign")
    public Result<DispatchVO> reassign(@PathVariable Long id, @Valid @RequestBody DispatchReassignDTO dto) {
        return Result.success(dispatchService.reassign(id, dto.getTargetUserId(), dto.getReason(),
                SecurityUtils.getUsername(), SecurityUtils.getUserId()));
    }

    @Operation(summary = "退回（RETURN：退回上级责任层，创建新的上级责任实例）")
    @SaCheckPermission("production:dispatch:assign")
    @PostMapping("/{id}/return")
    public Result<DispatchVO> returnTask(@PathVariable Long id, @Valid @RequestBody DispatchReturnDTO dto) {
        return Result.success(dispatchService.returnTask(id, dto.getReason(),
                SecurityUtils.getUsername(), SecurityUtils.getUserId()));
    }

    // ==================== P1-D 正式 ASSIGN（V1 API，无 level/transferFrom） ====================

    @Operation(summary = "初始派工（ASSIGN V1 正式 API；不含 level/transferFrom）",
            description = "Dispatch V1 前端专用。旧 /assign（DispatchAssignDTO）为 Legacy compatibility adapter，Do not use from Dispatch V1 frontend.")
    @SaCheckPermission("production:dispatch:assign")
    @PostMapping("/assign-v1")
    public Result<DispatchVO> assignV1(@Valid @RequestBody DispatchAssignV1DTO dto) {
        return Result.success(dispatchService.assignV1(dto, SecurityUtils.getUsername(), SecurityUtils.getUserId()));
    }

    @Operation(summary = "单工序指派/改派")
    @SaCheckPermission("production:dispatch:assign")
    @PostMapping("/assign")
    public Result<DispatchVO> assign(@Valid @RequestBody DispatchAssignDTO dto) {
        return Result.success(dispatchService.assign(dto, SecurityUtils.getUsername(), SecurityUtils.getUserId()));
    }

    @Operation(summary = "工单批量派工")
    @SaCheckPermission("production:dispatch:assign")
    @PostMapping("/batch-assign")
    public Result<Integer> batchAssign(@Valid @RequestBody DispatchAssignDTO dto) {
        return Result.success(dispatchService.batchAssign(dto, SecurityUtils.getUsername(), SecurityUtils.getUserId()));
    }

    @Operation(summary = "退回（原因必填）")
    @SaCheckPermission("production:dispatch:assign")
    @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id, @RequestBody Map<String, String> body) {
        dispatchService.reject(id, body.get("reason"), SecurityUtils.getUsername(), SecurityUtils.getUserId());
        return Result.success();
    }

    @Operation(summary = "开始")
    @SaCheckPermission("production:dispatch:start")
    @PostMapping("/{id}/start")
    public Result<Void> start(@PathVariable Long id) {
        dispatchService.start(id, SecurityUtils.getUsername(), SecurityUtils.getUserId());
        return Result.success();
    }

    @Operation(summary = "完成")
    @SaCheckPermission("production:dispatch:start")
    @PostMapping("/{id}/complete")
    public Result<Void> complete(@PathVariable Long id) {
        dispatchService.complete(id, SecurityUtils.getUsername(), SecurityUtils.getUserId());
        return Result.success();
    }

    @Operation(summary = "工单级责任班组/负责人")
    @SaCheckPermission("production:dispatch:assign")
    @PutMapping("/order/{orderId}/team")
    public Result<Void> updateOrderTeam(@PathVariable Long orderId,
                                        @RequestBody Map<String, Long> body) {
        dispatchService.updateOrderTeam(orderId, body.get("teamId"), body.get("leaderId"),
                SecurityUtils.getUsername());
        return Result.success();
    }
}
