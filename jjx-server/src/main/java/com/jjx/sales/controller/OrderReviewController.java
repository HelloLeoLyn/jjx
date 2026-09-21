package com.jjx.sales.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.common.core.result.Result;
import com.jjx.framework.common.controller.BaseController;
import com.jjx.sales.domain.entity.SalesOrder;
import com.jjx.sales.domain.vo.OrderReviewProcessVO;
import com.jjx.sales.service.IOrderReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 订单审核（只读）控制器。
 *
 * <p>2026-09-21（dev-20260921-010）：本控制器原先还挂着 11 个写接口
 * （/submit /start /approve /reject /return /transfer /customer/confirm /cancel /batch/*），
 * 它们前端 0 引用、服务的实现只动 mapper、不发任何事件，而且和 OrderStatusController
 * 是同一套业务动作的第二套实现（状态机校验也不一致）——属于会静默绕过通知与留痕的隐患，已删除。
 * 真正在用的审核动作入口是 OrderStatusController（/sales/orders/{orderId}/status/…）。
 * 本控制器只保留查询能力。</p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/sales/order/review")
@Tag(name = "订单审核查询")
public class OrderReviewController extends BaseController {

    private final IOrderReviewService orderReviewService;

    /**
     * 订单评审记录列表（读 review_flow，前端 review-print 页面在用）
     */
    @GetMapping("/records/{orderId}")
    @Operation(summary = "订单评审记录列表")
    @SaCheckPermission("sales:order:view")
    public Result<List<OrderReviewProcessVO>> records(@PathVariable Long orderId) {
        return Result.success(orderReviewService.getOrderReviewRecords(orderId));
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
        return Result.success(orderReviewService.canSubmitForReview(orderId));
    }

    /**
     * 检查订单是否可审核
     */
    @GetMapping("/canReview/{orderId}")
    @Operation(summary = "检查订单是否可审核")
    @SaCheckPermission("sales:order:view")
    public Result<Boolean> canReviewOrder(@PathVariable Long orderId, @RequestParam Long reviewerId) {
        return Result.success(orderReviewService.canReviewOrder(orderId, reviewerId));
    }

    /**
     * 检查订单是否可客户确认
     */
    @GetMapping("/canConfirm/{orderId}")
    @Operation(summary = "检查订单是否可客户确认")
    @SaCheckPermission("sales:order:view")
    public Result<Boolean> canConfirmByCustomer(@PathVariable Long orderId, @RequestParam Long customerId) {
        return Result.success(orderReviewService.canConfirmByCustomer(orderId, customerId));
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
}
