package com.jjx.sales.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.common.core.result.Result;
import com.jjx.framework.common.controller.BaseController;
import com.jjx.sales.domain.vo.OrderReviewProcessVO;
import com.jjx.sales.service.IOrderReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** 订单审核只读查询；审核动作统一由 OrderStatusController 承担。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/sales/order/review")
@Tag(name = "订单审核查询")
public class OrderReviewController extends BaseController {
    private final IOrderReviewService orderReviewService;

    @GetMapping("/records/{orderId}")
    @Operation(summary = "订单评审记录列表")
    @SaCheckPermission("sales:order:view")
    public Result<List<OrderReviewProcessVO>> records(@PathVariable Long orderId) {
        return Result.success(orderReviewService.getOrderReviewRecords(orderId));
    }

    @GetMapping("/canSubmit/{orderId}")
    @Operation(summary = "检查订单是否可提交审核")
    @SaCheckPermission("sales:order:view")
    public Result<Boolean> canSubmitForReview(@PathVariable Long orderId) {
        return Result.success(orderReviewService.canSubmitForReview(orderId));
    }

    @GetMapping("/canConfirm/{orderId}")
    @Operation(summary = "检查订单是否可由客户确认")
    @SaCheckPermission("sales:order:view")
    public Result<Boolean> canConfirmByCustomer(@PathVariable Long orderId, @RequestParam Long customerId) {
        return Result.success(orderReviewService.canConfirmByCustomer(orderId, customerId));
    }
}
