package com.jjx.sales.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.common.core.result.Result;
import com.jjx.common.exception.BusinessException;
import com.jjx.framework.common.controller.BaseController;
import com.jjx.sales.domain.entity.OrderReviewRecord;
import com.jjx.sales.domain.entity.SalesOrder;
import com.jjx.sales.service.IOrderReviewService;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.annotation.Log;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 订单审核控制器
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/sales/order/review")
@Tag(name = "订单审核管理")
public class OrderReviewController extends BaseController {

    private final IOrderReviewService orderReviewService;

    /**
     * 提交订单审核
     */
    @PostMapping("/submit/{orderId}")
    @Operation(summary = "提交订单审核")
    @Log(module = "订单审核管理", businessType = BusinessType.UPDATE, bizType = "'order'", bizId = "#orderId")
    @SaCheckPermission("sales:order:review")
    public Result<Long> submitOrderForReview(@PathVariable Long orderId,
                                             @RequestParam Long submitterId,
                                             @RequestParam String submitterName,
                                             @RequestParam(required = false) String submitComment) {
        Long recordId = orderReviewService.submitOrderForReview(orderId, submitterId, submitterName, submitComment);
        return Result.success(recordId);
    }

    /**
     * 开始审核订单
     */
    @PostMapping("/start/{orderId}")
    @Operation(summary = "开始审核订单")
    @Log(module = "订单审核管理", businessType = BusinessType.UPDATE, bizType = "'order'", bizId = "#orderId")
    @SaCheckPermission("sales:order:review")
    public Result<Long> startOrderReview(@PathVariable Long orderId,
                                         @RequestParam Long reviewerId,
                                         @RequestParam String reviewerName,
                                         @RequestParam String reviewerRole) {
        Long recordId = orderReviewService.startOrderReview(orderId, reviewerId, reviewerName, reviewerRole);
        return Result.success(recordId);
    }

    /**
     * 审核通过订单
     */
    @PostMapping("/approve/{orderId}")
    @Operation(summary = "审核通过订单")
    @Log(module = "订单审核管理", businessType = BusinessType.APPROVE, bizType = "'order'", bizId = "#orderId")
    @SaCheckPermission("sales:order:approve")
    public Result<Long> approveOrder(@PathVariable Long orderId,
                                     @RequestParam Long reviewerId,
                                     @RequestParam String reviewerName,
                                     @RequestParam(required = false) String reviewComment,
                                     @RequestParam(required = false) String attachments) {
        Long recordId = orderReviewService.approveOrder(orderId, reviewerId, reviewerName, reviewComment, attachments);
        return Result.success(recordId);
    }

    /**
     * 审核驳回订单
     */
    @PostMapping("/reject/{orderId}")
    @Operation(summary = "审核驳回订单")
    @Log(module = "订单审核管理", businessType = BusinessType.APPROVE, bizType = "'order'", bizId = "#orderId")
    @SaCheckPermission("sales:order:approve")
    public Result<Long> rejectOrder(@PathVariable Long orderId,
                                    @RequestParam Long reviewerId,
                                    @RequestParam String reviewerName,
                                    @RequestParam(required = false) String reviewComment,
                                    @RequestParam String rejectReason,
                                    @RequestParam(required = false) String improvementSuggestions) {
        Long recordId = orderReviewService.rejectOrder(orderId, reviewerId, reviewerName, reviewComment, rejectReason, improvementSuggestions);
        return Result.success(recordId);
    }

    /**
     * 退回订单修改
     */
    @PostMapping("/return/{orderId}")
    @Operation(summary = "退回订单修改")
    @Log(module = "订单审核管理", businessType = BusinessType.UPDATE, bizType = "'order'", bizId = "#orderId")
    @SaCheckPermission("sales:order:review")
    public Result<Long> returnOrderForModification(@PathVariable Long orderId,
                                                   @RequestParam Long reviewerId,
                                                   @RequestParam String reviewerName,
                                                   @RequestParam(required = false) String reviewComment,
                                                   @RequestParam String returnReason,
                                                   @RequestParam(required = false) String modificationRequirements) {
        Long recordId = orderReviewService.returnOrderForModification(orderId, reviewerId, reviewerName, reviewComment, returnReason, modificationRequirements);
        return Result.success(recordId);
    }

    /**
     * 转交审核
     */
    @PostMapping("/transfer/{orderId}")
    @Operation(summary = "转交审核")
    @Log(module = "订单审核管理", businessType = BusinessType.UPDATE, bizType = "'order'", bizId = "#orderId")
    @SaCheckPermission("sales:order:review")
    public Result<Long> transferOrderReview(@PathVariable Long orderId,
                                            @RequestParam Long currentReviewerId,
                                            @RequestParam Long nextReviewerId,
                                            @RequestParam String nextReviewerName,
                                            @RequestParam String transferReason) {
        Long recordId = orderReviewService.transferOrderReview(orderId, currentReviewerId, nextReviewerId, nextReviewerName, transferReason);
        return Result.success(recordId);
    }

    /**
     * 客户确认订单
     */
    @PostMapping("/customer/confirm/{orderId}")
    @Operation(summary = "客户确认订单")
    @Log(module = "订单审核管理", businessType = BusinessType.UPDATE, bizType = "'order'", bizId = "#orderId")
    @SaCheckPermission("sales:order:review")
    public Result<Long> confirmOrderByCustomer(@PathVariable Long orderId,
                                               @RequestParam Long customerId,
                                               @RequestParam String customerName,
                                               @RequestParam(required = false) String confirmComment,
                                               @RequestParam(required = false) String customerFeedback) {
        Long recordId = orderReviewService.confirmOrderByCustomer(orderId, customerId, customerName, confirmComment, customerFeedback);
        return Result.success(recordId);
    }

    /**
     * 取消订单审核
     */
    @PostMapping("/cancel/{orderId}")
    @Operation(summary = "取消订单审核")
    @Log(module = "订单审核管理", businessType = BusinessType.UPDATE, bizType = "'order'", bizId = "#orderId")
    @SaCheckPermission("sales:order:review")
    public Result<Long> cancelOrderReview(@PathVariable Long orderId,
                                          @RequestParam Long cancellerId,
                                          @RequestParam String cancellerName,
                                          @RequestParam String cancelReason) {
        Long recordId = orderReviewService.cancelOrderReview(orderId, cancellerId, cancellerName, cancelReason);
        return Result.success(recordId);
    }

    /**
     * 获取订单审核记录列表
     */
    @GetMapping("/records/{orderId}")
    @Operation(summary = "获取订单审核记录列表")
    @SaCheckPermission("sales:order:view")
    public Result<List<OrderReviewRecord>> getOrderReviewRecords(@PathVariable Long orderId) {
        List<OrderReviewRecord> records = orderReviewService.getOrderReviewRecords(orderId);
        return Result.success(records);
    }

    /**
     * 获取订单审核历史
     */
    @GetMapping("/history/{orderId}")
    @Operation(summary = "获取订单审核历史")
    @SaCheckPermission("sales:order:view")
    public Result<List<OrderReviewRecord>> getOrderReviewHistory(@PathVariable Long orderId) {
        List<OrderReviewRecord> history = orderReviewService.getOrderReviewHistory(orderId);
        return Result.success(history);
    }

    /**
     * 获取当前审核信息
     */
    @GetMapping("/current/{orderId}")
    @Operation(summary = "获取当前审核信息")
    @SaCheckPermission("sales:order:view")
    public Result<OrderReviewRecord> getCurrentReviewInfo(@PathVariable Long orderId) {
        OrderReviewRecord currentInfo = orderReviewService.getCurrentReviewInfo(orderId);
        return Result.success(currentInfo);
    }

    /**
     * 获取待审核订单列表
     */
    @GetMapping("/pending/{reviewerId}")
    @Operation(summary = "获取待审核订单列表")
    @SaCheckPermission("sales:order:view")
    public Result<?> getPendingReviewOrders(@PathVariable Long reviewerId) {
        return Result.success(orderReviewService.getPendingReviewOrders(reviewerId));
    }

    /**
     * 获取已提交审核订单列表
     */
    @GetMapping("/submitted/{submitterId}")
    @Operation(summary = "获取已提交审核订单列表")
    @SaCheckPermission("sales:order:view")
    public Result<?> getSubmittedReviewOrders(@PathVariable Long submitterId) {
       return Result.success(orderReviewService.getSubmittedReviewOrders(submitterId));
    }

    /**
     * 检查订单是否可提交审核
     */
    @GetMapping("/canSubmit/{orderId}")
    @Operation(summary = "检查订单是否可提交审核")
    @SaCheckPermission("sales:order:view")
    public Result<Boolean> canSubmitForReview(@PathVariable Long orderId) {
        boolean canSubmit = orderReviewService.canSubmitForReview(orderId);
        return Result.success(canSubmit);
    }

    /**
     * 检查订单是否可审核
     */
    @GetMapping("/canReview/{orderId}")
    @Operation(summary = "检查订单是否可审核")
    @SaCheckPermission("sales:order:view")
    public Result<Boolean> canReviewOrder(@PathVariable Long orderId,
                                          @RequestParam Long reviewerId) {
        boolean canReview = orderReviewService.canReviewOrder(orderId, reviewerId);
        return Result.success(canReview);
    }

    /**
     * 检查订单是否可客户确认
     */
    @GetMapping("/canConfirm/{orderId}")
    @Operation(summary = "检查订单是否可客户确认")
    @SaCheckPermission("sales:order:view")
    public Result<Boolean> canConfirmByCustomer(@PathVariable Long orderId,
                                                @RequestParam Long customerId) {
        boolean canConfirm = orderReviewService.canConfirmByCustomer(orderId, customerId);
        return Result.success(canConfirm);
    }

    /**
     * 获取审核超时订单列表
     */
    @GetMapping("/timeout")
    @Operation(summary = "获取审核超时订单列表")
    @SaCheckPermission("sales:order:view")
    public Result<List<SalesOrder>> getTimeoutReviewOrders(@RequestParam(defaultValue = "24") Integer timeoutHours) {
        return Result.success(orderReviewService.getTimeoutReviewOrders(timeoutHours));
    }

    /**
     * 批量提交审核
     */
    @PostMapping("/batch/submit")
    @Operation(summary = "批量提交审核")
    @Log(module = "订单审核管理", businessType = BusinessType.UPDATE, bizType = "'order'", bizId = "#orderId")
    @SaCheckPermission("sales:order:review")
    public Result<Object> batchSubmitForReview(@RequestParam List<Long> orderIds,
                                               @RequestParam Long submitterId,
                                               @RequestParam String submitterName) {
        Object result = orderReviewService.batchSubmitForReview(orderIds, submitterId, submitterName);
        return Result.success(result);
    }

    /**
     * 批量审核通过
     */
    @PostMapping("/batch/approve")
    @Operation(summary = "批量审核通过")
    @Log(module = "订单审核管理", businessType = BusinessType.APPROVE, bizType = "'order'", bizId = "#orderId")
    @SaCheckPermission("sales:order:approve")
    public Result<Object> batchApproveOrders(@RequestParam List<Long> orderIds,
                                             @RequestParam Long reviewerId,
                                             @RequestParam String reviewerName) {
        Object result = orderReviewService.batchApproveOrders(orderIds, reviewerId, reviewerName);
        return Result.success(result);
    }

    /**
     * 批量审核驳回
     */
    @PostMapping("/batch/reject")
    @Operation(summary = "批量审核驳回")
    @Log(module = "订单审核管理", businessType = BusinessType.APPROVE, bizType = "'order'", bizId = "#orderId")
    @SaCheckPermission("sales:order:approve")
    public Result<Object> batchRejectOrders(@RequestParam List<Long> orderIds,
                                            @RequestParam Long reviewerId,
                                            @RequestParam String reviewerName,
                                            @RequestParam String rejectReason) {
        Object result = orderReviewService.batchRejectOrders(orderIds, reviewerId, reviewerName, rejectReason);
        return Result.success(result);
    }
}
