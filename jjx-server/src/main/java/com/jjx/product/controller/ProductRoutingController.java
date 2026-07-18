package com.jjx.product.controller;

import com.jjx.common.core.page.PageResult;
import com.jjx.common.core.result.Result;
import com.jjx.product.domain.dto.ProductRoutingDTO;
import com.jjx.product.domain.dto.ProductRoutingQueryDTO;
import com.jjx.product.domain.vo.ProductRoutingVO;
import com.jjx.product.service.IProductRoutingService;
import com.jjx.product.service.ProductRoutingValidator;
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
@RequestMapping("/product/routings")
@RequiredArgsConstructor
@Validated
public class ProductRoutingController {

    private final IProductRoutingService routingService;
    private final ProductRoutingValidator routingValidator;

    // ==================== 基础 CRUD ====================

    @Operation(summary = "分页查询工艺路线")
    @GetMapping("/page")
    public Result<PageResult<ProductRoutingVO>> pageQuery(ProductRoutingQueryDTO dto) {
        return Result.success(routingService.pageQuery(dto));
    }

    @Operation(summary = "获取工艺路线详情")
    @GetMapping("/{routingId}")
    public Result<ProductRoutingVO> getById(
            @Parameter(description = "路线ID", required = true)
            @PathVariable @NotNull Long routingId) {
        ProductRoutingVO vo = routingService.getRoutingItems(routingId);
        return Result.success(vo);
    }

    @Operation(summary = "创建工艺路线")
    @PostMapping
    public Result<ProductRoutingVO> create(@Valid @RequestBody ProductRoutingDTO dto) {
        ProductRoutingVO vo = routingService.createRouting(dto);
        return Result.success(vo);
    }

    @Operation(summary = "更新工艺路线")
    @PutMapping("/{routingId}")
    public Result<ProductRoutingVO> update(
            @Parameter(description = "路线ID", required = true)
            @PathVariable @NotNull Long routingId,
            @Valid @RequestBody ProductRoutingDTO dto) {
        dto.setRoutingId(routingId);
        ProductRoutingVO vo = routingService.updateRouting(dto);
        return Result.success(vo);
    }

    @Operation(summary = "删除工艺路线")
    @DeleteMapping("/{routingId}")
    public Result<Void> delete(
            @Parameter(description = "路线ID", required = true)
            @PathVariable @NotNull Long routingId) {
        routingService.removeById(routingId);
        return Result.success();
    }

    // ==================== 版本管理 ====================

    @Operation(summary = "复制为新版本")
    @PostMapping("/{routingId}/copy")
    public Result<ProductRoutingVO> copyAsNewVersion(
            @Parameter(description = "路线ID", required = true)
            @PathVariable @NotNull Long routingId,
            @Parameter(description = "新版本号", required = true)
            @RequestParam String newVersion) {
        ProductRoutingVO vo = routingService.copyAsNewVersion(routingId, newVersion);
        return Result.success(vo);
    }

    @Operation(summary = "设置当前版本")
    @PutMapping("/{routingId}/set-current")
    public Result<Void> setCurrentVersion(
            @Parameter(description = "路线ID", required = true)
            @PathVariable @NotNull Long routingId) {
        routingService.setCurrentVersion(routingId);
        return Result.success();
    }

    @Operation(summary = "获取产品所有版本")
    @GetMapping("/product/{productId}/versions")
    public Result<List<ProductRoutingVO>> getAllVersionsByProductId(
            @Parameter(description = "产品ID", required = true)
            @PathVariable @NotNull Long productId) {
        List<ProductRoutingVO> list = routingService.getAllVersionsByProductId(productId);
        return Result.success(list);
    }

    @Operation(summary = "获取产品已审批的工艺路线列表（用于产品新增/编辑时选择）")
    @GetMapping("/product/{productId}/approved")
    public Result<List<ProductRoutingVO>> getApprovedRoutings(
            @Parameter(description = "产品ID", required = true)
            @PathVariable @NotNull Long productId) {
        ProductRoutingQueryDTO dto = new ProductRoutingQueryDTO();
        dto.setProductId(productId);
        dto.setApproveStatus(3); // 已审批状态
        dto.setPageSize(Integer.MAX_VALUE);
        PageResult<ProductRoutingVO> pageResult = routingService.pageQuery(dto);
        return Result.success(pageResult.getRecords());
    }

    @Operation(summary = "获取产品当前版本")
    @GetMapping("/product/{productId}/current")
    public Result<ProductRoutingVO> getCurrentByProductId(
            @Parameter(description = "产品ID", required = true)
            @PathVariable @NotNull Long productId) {
        ProductRoutingVO vo = routingService.getCurrentByProductId(productId);
        return Result.success(vo);
    }

    // ==================== 审批流程 ====================

    @Operation(summary = "提交审批")
    @PostMapping("/{routingId}/submit")
    public Result<Void> submitApprove(
            @Parameter(description = "路线ID", required = true)
            @PathVariable @NotNull Long routingId) {
        routingService.submitApprove(routingId);
        return Result.success();
    }

    @Operation(summary = "审批通过")
    @PutMapping("/{routingId}/approve")
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
