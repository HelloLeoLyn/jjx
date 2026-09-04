package com.jjx.product.controller;

import com.jjx.common.constant.LogActions;
import com.jjx.common.core.page.PageResult;
import com.jjx.common.core.result.Result;
import com.jjx.product.domain.dto.EngineeringRoutingDTO;
import com.jjx.product.domain.dto.EngineeringRoutingQueryDTO;
import com.jjx.product.domain.vo.EngineeringRoutingVO;
import com.jjx.product.service.IEngineeringRoutingService;
import com.jjx.product.service.EngineeringRoutingValidator;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.annotation.Log;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 产品工艺路线控制器
 */
@Tag(name = "产品工艺路线管理", description = "产品工艺路线相关接口")
@RestController
@RequestMapping("/engineering/routings")
@RequiredArgsConstructor
@Validated
public class EngineeringRoutingController {

    private final IEngineeringRoutingService routingService;
    private final EngineeringRoutingValidator routingValidator;

    // ==================== 基础 CRUD ====================

    @Operation(summary = "分页查询工艺路线")
    @GetMapping("/page")
    public Result<PageResult<EngineeringRoutingVO>> pageQuery(EngineeringRoutingQueryDTO dto) {
        return Result.success(routingService.pageQuery(dto));
    }

    @Operation(summary = "获取工艺路线详情")
    @GetMapping("/{routingId}")
    public Result<EngineeringRoutingVO> getById(
            @Parameter(description = "路线ID", required = true)
            @PathVariable @NotNull Long routingId) {
        EngineeringRoutingVO vo = routingService.getRoutingItems(routingId);
        return Result.success(vo);
    }

    @Operation(summary = "创建工艺路线")
    @PostMapping
    @Log(module = "工艺路线管理", businessType = BusinessType.INSERT, bizType = "'routing'",
         bizId = "#result.data.routingId", action = LogActions.ROUTING_CREATE)
    @SaCheckPermission("engineering:routing:add")
    public Result<EngineeringRoutingVO> create(@Valid @RequestBody EngineeringRoutingDTO dto) {
        EngineeringRoutingVO vo = routingService.createRouting(dto);
        return Result.success(vo);
    }

    @Operation(summary = "更新工艺路线")
    @PutMapping("/{routingId}")
    @Log(module = "工艺路线管理", businessType = BusinessType.UPDATE, bizType = "'routing'",
         bizId = "#routingId", action = LogActions.ROUTING_EDIT)
    @SaCheckPermission("engineering:routing:edit")
    public Result<EngineeringRoutingVO> update(
            @Parameter(description = "路线ID", required = true)
            @PathVariable @NotNull Long routingId,
            @Valid @RequestBody EngineeringRoutingDTO dto) {
        dto.setRoutingId(routingId);
        EngineeringRoutingVO vo = routingService.updateRouting(dto);
        return Result.success(vo);
    }

    @Operation(summary = "删除工艺路线")
    @DeleteMapping("/{routingId}")
    @Log(module = "工艺路线管理", businessType = BusinessType.DELETE, bizType = "'routing'",
         bizId = "#routingId", action = LogActions.ROUTING_DELETE)
    @SaCheckPermission("engineering:routing:delete")
    public Result<Void> delete(
            @Parameter(description = "路线ID", required = true)
            @PathVariable @NotNull Long routingId) {
        routingService.removeById(routingId);
        return Result.success();
    }

    // ==================== 版本管理 ====================

    @Operation(summary = "复制为新版本")
    @PostMapping("/{routingId}/copy")
    @Log(module = "工艺路线管理", businessType = BusinessType.UPDATE, bizType = "'routing'",
         bizId = "#result.data.routingId", action = LogActions.ROUTING_COPY_VERSION)
    @SaCheckPermission("engineering:routing:add")
    public Result<EngineeringRoutingVO> copyAsNewVersion(
            @Parameter(description = "路线ID", required = true)
            @PathVariable @NotNull Long routingId,
            @Parameter(description = "新版本号", required = true)
            @RequestParam String newVersion) {
        EngineeringRoutingVO vo = routingService.copyAsNewVersion(routingId, newVersion);
        return Result.success(vo);
    }

    @Operation(summary = "设置当前版本")
    @PutMapping("/{routingId}/set-current")
    @Log(module = "工艺路线管理", businessType = BusinessType.UPDATE, bizType = "'routing'",
         bizId = "#routingId", action = LogActions.ROUTING_SET_CURRENT)
    @SaCheckPermission("engineering:routing:edit")
    public Result<Void> setCurrentVersion(
            @Parameter(description = "路线ID", required = true)
            @PathVariable @NotNull Long routingId) {
        routingService.setCurrentVersion(routingId);
        return Result.success();
    }

    @Operation(summary = "获取产品所有版本")
    @GetMapping("/product/{productId}/versions")
    public Result<List<EngineeringRoutingVO>> getAllVersionsByProductId(
            @Parameter(description = "产品ID", required = true)
            @PathVariable @NotNull Long productId) {
        List<EngineeringRoutingVO> list = routingService.getAllVersionsByProductId(productId);
        return Result.success(list);
    }

    @Operation(summary = "获取产品已审批的工艺路线列表（用于产品新增/编辑时选择）")
    @GetMapping("/product/{productId}/approved")
    public Result<List<EngineeringRoutingVO>> getApprovedRoutings(
            @Parameter(description = "产品ID", required = true)
            @PathVariable @NotNull Long productId) {
        EngineeringRoutingQueryDTO dto = new EngineeringRoutingQueryDTO();
        dto.setProductId(productId);
        dto.setApproveStatus(3); // 已审批状态
        dto.setPageSize(Integer.MAX_VALUE);
        PageResult<EngineeringRoutingVO> pageResult = routingService.pageQuery(dto);
        return Result.success(pageResult.getRecords());
    }

    @Operation(summary = "获取产品当前版本")
    @GetMapping("/product/{productId}/current")
    public Result<EngineeringRoutingVO> getCurrentByProductId(
            @Parameter(description = "产品ID", required = true)
            @PathVariable @NotNull Long productId) {
        EngineeringRoutingVO vo = routingService.getCurrentByProductId(productId);
        return Result.success(vo);
    }

    // ==================== 审批流程 ====================

    @Operation(summary = "提交审批")
    @PostMapping("/{routingId}/submit")
    @Log(module = "工艺路线管理", businessType = BusinessType.UPDATE, bizType = "'routing'",
         bizId = "#routingId", action = LogActions.ROUTING_SUBMIT)
    @SaCheckPermission("engineering:routing:edit")
    public Result<Void> submitApprove(
            @Parameter(description = "路线ID", required = true)
            @PathVariable @NotNull Long routingId) {
        routingService.submitApprove(routingId);
        return Result.success();
    }

    @Operation(summary = "审批通过")
    @PutMapping("/{routingId}/approve")
    @Log(module = "工艺路线管理", businessType = BusinessType.APPROVE, bizType = "'routing'",
         bizId = "#routingId", action = LogActions.ROUTING_APPROVE)
    @SaCheckPermission("engineering:routing:approve")
    public Result<Void> approve(
            @Parameter(description = "路线ID", required = true)
            @PathVariable @NotNull Long routingId,
            @Parameter(description = "审批意见")
            @RequestParam(required = false) String remark) {
        routingService.approve(routingId, remark);
        return Result.success();
    }

    @Operation(summary = "审批驳回")
    @PutMapping("/{routingId}/reject")
    @Log(module = "工艺路线管理", businessType = BusinessType.UPDATE, bizType = "'routing'",
         bizId = "#routingId", action = LogActions.ROUTING_REJECT)
    @SaCheckPermission("engineering:routing:reject")
    public Result<Void> reject(
            @Parameter(description = "路线ID", required = true)
            @PathVariable @NotNull Long routingId,
            @Parameter(description = "驳回原因", required = true)
            @RequestParam String remark) {
        routingService.reject(routingId, remark);
        return Result.success();
    }

    // ==================== 工时计算 ====================

    @Operation(summary = "重新计算工时")
    @PostMapping("/{routingId}/calculate-hours")
    public Result<Void> calculateHours(
            @Parameter(description = "路线ID", required = true)
            @PathVariable @NotNull Long routingId) {
        routingService.calculateHours(routingId);
        return Result.success();
    }

    @Operation(summary = "验证工艺路线")
    @GetMapping("/{routingId}/validate")
    public Result<Boolean> validateRouting(
            @Parameter(description = "路线ID", required = true)
            @PathVariable @NotNull Long routingId) {
        boolean result = routingValidator.validateRouting(routingId);
        return Result.success(result);
    }
}
