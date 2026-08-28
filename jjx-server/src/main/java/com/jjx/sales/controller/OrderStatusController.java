package com.jjx.sales.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.common.core.result.Result;
import com.jjx.sales.domain.dto.ReviewDTO;
import com.jjx.sales.domain.vo.ReviewHistoryVO;
import com.jjx.sales.domain.vo.ReviewStatusVO;
import com.jjx.sales.service.IOrderStatusService;
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

@Tag(name = "订单状态管理", description = "订单审核流程相关接口")
@RestController
@RequestMapping("/sales/orders")
@RequiredArgsConstructor
@Validated
public class OrderStatusController {

    private final IOrderStatusService orderStatusService;

    /**
     * 提交审核
     */
    @Operation(summary = "提交审核")
    @Log(module = "订单状态管理", businessType = BusinessType.UPDATE, bizType = "'order'", bizId = "#orderId", bizStatus = "2",
            detail = "'{\"changes\":[\"订单状态：草稿/已驳回 → 待审核\"]}'")
    @SaCheckPermission("sales:order:submit")
    @PutMapping("/{orderId}/status/submissions")
    public Result<Void> submitReview(
            @Parameter(description = "订单ID", required = true)
            @PathVariable @NotNull Long orderId) {
        orderStatusService.submitReview(orderId);
        return Result.success();
    }

    /**
     * 开始审核
     */
    @Operation(summary = "开始审核")
    @Log(module = "订单状态管理", businessType = BusinessType.UPDATE, bizType = "'order'", bizId = "#orderId", bizStatus = "3",
            detail = "'{\"changes\":[\"订单状态：待审核 → 审核中\"]}'")
    @SaCheckPermission("sales:order:review")
    @PutMapping("/{orderId}/status/review")
    public Result<Void> startReview(
            @Parameter(description = "订单ID", required = true)
            @PathVariable @NotNull Long orderId) {
        orderStatusService.startReview(orderId);
        return Result.success();
    }

    /**
     * 审核通过
     */
    @Operation(summary = "审核通过")
    @Log(module = "订单状态管理", businessType = BusinessType.APPROVE, bizType = "'order'", bizId = "#orderId", bizStatus = "4",
            detail = "'{\"changes\":[\"订单状态：审核中 → 已审核\"]}'")
    @SaCheckPermission("sales:order:approve")
    @PutMapping("/{orderId}/status/approval")
    public Result<Void> approveOrder(
            @Parameter(description = "订单ID", required = true)
            @PathVariable @NotNull Long orderId,
            @Valid @RequestBody ReviewDTO reviewDTO) {
        reviewDTO.setOrderId(orderId);
        orderStatusService.approveOrder(reviewDTO);
        return Result.success();
    }

    /**
     * 审核驳回
     */
    @Operation(summary = "审核驳回")
    @Log(module = "订单状态管理", businessType = BusinessType.APPROVE, bizType = "'order'", bizId = "#orderId", bizStatus = "5",
            detail = "'{\"changes\":[\"订单状态：审核中 → 已驳回\"]}'")
    @SaCheckPermission("sales:order:approve")
    @PutMapping("/{orderId}/status/rejection")
    public Result<Void> rejectOrder(
            @Parameter(description = "订单ID", required = true)
            @PathVariable @NotNull Long orderId,
            @Valid @RequestBody ReviewDTO reviewDTO) {
        reviewDTO.setOrderId(orderId);
        orderStatusService.rejectOrder(reviewDTO);
        return Result.success();
    }

    /**
     * 重新提交审核（驳回后）
     */
    @Operation(summary = "重新提交审核（驳回后）")
    @Log(module = "订单状态管理", businessType = BusinessType.UPDATE, bizType = "'order'", bizId = "#orderId", bizStatus = "2",
            detail = "'{\"changes\":[\"订单状态：已驳回 → 待审核\"]}'")
    @SaCheckPermission("sales:order:submit")
    @PutMapping("/{orderId}/status/resubmissions")
    public Result<Void> resubmit(
            @Parameter(description = "订单ID", required = true)
            @PathVariable @NotNull Long orderId) {
        orderStatusService.resubmit(orderId);
        return Result.success();
    }

    /**
     * 取消订单
     */
    @Operation(summary = "取消订单")
    @Log(module = "订单状态管理", businessType = BusinessType.UPDATE, bizType = "'order'", bizId = "#orderId", bizStatus = "10",
            detail = "'{\"changes\":[\"订单状态：可取消状态 → 已取消\",\"关联生产工单按规则联动取消\"]}'")
    @SaCheckPermission("sales:order:edit")
    @DeleteMapping("/{orderId}/status")
    public Result<Void> cancelOrder(
            @Parameter(description = "订单ID", required = true)
            @PathVariable @NotNull Long orderId,
            @Parameter(description = "取消原因")
            @RequestParam(required = false) String reason) {
        orderStatusService.cancelOrder(orderId, reason);
        return Result.success();
    }

    /**
     * 获取订单审核状态
     */
    @Operation(summary = "获取订单审核状态")
    @SaCheckPermission("sales:order:view")
    @GetMapping("/{orderId}/reviews/status")
    public Result<ReviewStatusVO> getReviewStatus(
            @Parameter(description = "订单ID", required = true)
            @PathVariable @NotNull Long orderId) {
        ReviewStatusVO status = orderStatusService.getReviewStatus(orderId);
        return Result.success(status);
    }

    /**
     * 获取订单审核历史
     */
    @Operation(summary = "获取订单审核历史")
    @SaCheckPermission("sales:order:view")
    @GetMapping("/{orderId}/reviews/history")
    public Result<List<ReviewHistoryVO>> getReviewHistory(
            @Parameter(description = "订单ID", required = true)
            @PathVariable @NotNull Long orderId) {
        List<ReviewHistoryVO> history = orderStatusService.getReviewHistory(orderId);
        return Result.success(history);
    }

    /**
     * 生成生产计划（标准模式：SO→PLAN，审批后转工单）
     */
    @Operation(summary = "生成生产计划（标准模式：SO→PLAN→审批→转工单）")
    @Log(module = "订单状态管理", businessType = BusinessType.UPDATE, bizType = "'order'", bizId = "#orderId", bizStatus = "6",
            detail = "'{\"changes\":[\"生成生产计划；已审核订单同步 → 已确认\"]}'")
    @SaCheckPermission("sales:order:edit")
    @PutMapping("/{orderId}/status/generate-plan")
    public Result<Void> generatePlan(
            @Parameter(description = "订单ID", required = true)
            @PathVariable @NotNull Long orderId) {
        orderStatusService.createProductionPlan(orderId);
        return Result.success();
    }

    /**
     * 发货（025：生产中→已发货）
     */
    @Operation(summary = "发货（生产中→已发货，联动创建销售出库单并扣产品库存）")
    @Log(module = "订单状态管理", businessType = BusinessType.UPDATE, bizType = "'order'", bizId = "#orderId", bizStatus = "8",
            detail = "'{\"changes\":[\"订单状态：生产中 → 已发货\"]}'")
    @SaCheckPermission("sales:order:edit")
    @PutMapping("/{orderId}/status/ship")
    public Result<Void> shipOrder(
            @Parameter(description = "订单ID", required = true)
            @PathVariable @NotNull Long orderId) {
        orderStatusService.shipOrder(orderId);
        return Result.success();
    }

    /**
     * 完成订单
     */
    @Operation(summary = "完成订单")
    @Log(module = "订单状态管理", businessType = BusinessType.UPDATE, bizType = "'order'", bizId = "#orderId", bizStatus = "9",
            detail = "'{\"changes\":[\"订单状态：已发货 → 已完成\"]}'")
    @SaCheckPermission("sales:order:edit")
    @PutMapping("/{orderId}/status/complete")
    public Result<Void> completeOrder(
            @Parameter(description = "订单ID", required = true)
            @PathVariable @NotNull Long orderId) {
        orderStatusService.completeOrder(orderId);
        return Result.success();
    }

    /**
     * 客户确认订单
     */
    @Operation(summary = "客户确认订单")
    @Log(module = "订单状态管理", businessType = BusinessType.UPDATE, bizId = "#orderId", bizType = "'order'", bizStatus = "6",
            detail = "'{\"changes\":[\"订单状态：已审核 → 已确认\"]}'")
    @SaCheckPermission("sales:order:edit")
    @PutMapping("/{orderId}/confirm")
    public Result<Void> confirmOrder(@PathVariable Long orderId,
                                     @RequestParam String confirmedBy,
                                     @RequestParam(required = false) String confirmMethod,
                                     @RequestParam(required = false) String remark) {
        orderStatusService.confirmOrder(orderId, confirmedBy, confirmMethod, remark);
        return Result.success();
    }
}
