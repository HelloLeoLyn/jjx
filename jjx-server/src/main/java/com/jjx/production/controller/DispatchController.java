package com.jjx.production.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.common.core.page.PageResult;
import com.jjx.common.core.result.Result;
import com.jjx.framework.common.controller.BaseController;
import com.jjx.production.domain.dto.DispatchAssignDTO;
import com.jjx.production.domain.dto.DispatchQueryDTO;
import com.jjx.production.domain.entity.ProductionDispatchLog;
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

    @Operation(summary = "责任班组可选执行人（该班组+上级部门成员，向上递归）")
    @SaCheckPermission("production:dispatch:list")
    @GetMapping("/team-persons/{teamId}")
    public Result<List<com.jjx.system.domain.vo.SysUserVO>> teamPersons(@PathVariable Long teamId) {
        return Result.success(dispatchService.teamPersons(teamId));
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
