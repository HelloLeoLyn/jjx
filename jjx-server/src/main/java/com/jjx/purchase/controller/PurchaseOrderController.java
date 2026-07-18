package com.jjx.purchase.controller;

import com.jjx.common.core.page.PageResult;
import com.jjx.common.core.result.Result;
import com.jjx.framework.common.controller.BaseController;
import com.jjx.purchase.domain.dto.PurchaseOrderApprovalDTO;
import com.jjx.purchase.domain.dto.PurchaseOrderDTO;
import com.jjx.purchase.domain.dto.PurchaseOrderQueryDTO;
import com.jjx.purchase.domain.dto.PurchaseOrderReceiveDTO;
import com.jjx.purchase.domain.vo.PurchaseOrderItemVO;
import com.jjx.purchase.domain.vo.PurchaseOrderVO;
import com.jjx.purchase.service.IPurchaseOrderService;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.annotation.Log;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * 采购订单Controller
 */
@Slf4j
@RestController
@RequestMapping("/purchase/order")
@RequiredArgsConstructor
public class PurchaseOrderController extends BaseController {

    private final IPurchaseOrderService purchaseOrderService;

    /**
     * 查询采购订单列表
     */
    @GetMapping("/list")
    public Result<PageResult<PurchaseOrderVO>> list(PurchaseOrderQueryDTO queryDTO) {
        return Result.success(purchaseOrderService.page(queryDTO));
    }

    /**
     * 获取采购订单详细信息
     */
    @GetMapping("/{orderId}")
    public Result<PurchaseOrderVO> getInfo(@PathVariable Long orderId) {
        return Result.success(purchaseOrderService.selectOrderById(orderId));
    }

    /**
     * 获取采购订单详细信息
     */
    @GetMapping("/{orderId}/items")
    public Result<List<PurchaseOrderItemVO>> items(@PathVariable Long orderId) {
        return Result.success(purchaseOrderService.selectOrderItemsById(orderId));
    }

    /**
     * 新增采购订单
     */
    @PostMapping
    @Log(module = "采购订单管理", businessType = BusinessType.INSERT)
    @SaCheckPermission("purchase:order:add")
    public Result<Void> add(@Valid @RequestBody PurchaseOrderDTO orderDTO) {
        purchaseOrderService.insertOrder(orderDTO);
        return Result.success();
    }

    /**
     * 修改采购订单
     */
    @PutMapping
    @Log(module = "采购订单管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("purchase:order:edit")
    public Result<Void> edit(@Valid @RequestBody PurchaseOrderDTO orderDTO) {
        purchaseOrderService.updateOrder(orderDTO);
        return Result.success();
    }

    /**
     * 取消采购订单
     */
    @PutMapping("/cancel/{orderId}")
    @Log(module = "采购订单管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("purchase:order:edit")
    public Result<Void> cancel(@PathVariable Long orderId) {
        purchaseOrderService.cancelOrder(orderId);
        return Result.success();
    }

    /**
     * 提交审批
     */
    @PutMapping("/submit/{orderId}")
    @Log(module = "采购订单管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("purchase:order:edit")
    public Result<Void> submit(@PathVariable Long orderId) {
        purchaseOrderService.submitOrder(orderId);
        return Result.success();
    }

    /**
     * 批量提交审批
     */
    @PutMapping("/batch-submit")
    @Log(module = "采购订单管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("purchase:order:edit")
    public Result<Void> batchSubmit(@RequestBody List<Long> orderIds) {
        purchaseOrderService.batchSubmitOrders(orderIds);
        return Result.success();
    }

    /**
     * 审批订单
     */
    @PutMapping("/approve")
    @Log(module = "采购订单管理", businessType = BusinessType.APPROVE)
    @SaCheckPermission("purchase:order:approve")
    public Result<Void> approve(@Valid @RequestBody PurchaseOrderApprovalDTO dto) {
        purchaseOrderService.approveOrder(dto);
        return Result.success();
    }

    /**
     * 更新订单审批状态
     */
    @PutMapping("/status")
    @Log(module = "采购订单管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("purchase:order:edit")
    public Result<Void> updateStatus(@RequestParam Long orderId, @RequestParam Integer approvalStatus) {
        purchaseOrderService.updateOrderStatus(orderId, approvalStatus);
        return Result.success();
    }

    /**
     * 批量收货（含检验）
     * 使用DTO模式，一次请求可同时收货多个明细项
     */
    @PostMapping("/{orderId}/receive")
    @Log(module = "采购订单管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("purchase:order:edit")
    public Result<Void> receive(@PathVariable Long orderId, @Valid @RequestBody PurchaseOrderReceiveDTO dto) {
        dto.setOrderId(orderId);
        purchaseOrderService.batchReceiveOrderItems(dto);
        return Result.success();
    }

    /**
     * 更新收货状态
     */
    @PutMapping("/receiptStatus")
    @Log(module = "采购订单管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("purchase:order:edit")
    public Result<Void> updateReceiptStatus(@RequestParam Long orderId, @RequestParam Integer receiptStatus) {
        purchaseOrderService.updateReceiptStatus(orderId, receiptStatus);
        return Result.success();
    }

    /**
     * 更新付款信息
     */
    @PutMapping("/payment")
    @Log(module = "采购订单管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("purchase:order:edit")
    public Result<Integer> updatePayment(@RequestParam Long orderId,
                                      @RequestParam(required = false) BigDecimal paidAmount,
                                      @RequestParam Integer paymentStatus) {
        return Result.success(purchaseOrderService.updatePaymentInfo(orderId, paidAmount, paymentStatus));
    }

    /**
     * 获取订单统计信息
     */
    @GetMapping("/statistics")
    @SaCheckPermission("purchase:order:view")
    public Result<Object> statistics() {
        return Result.success(purchaseOrderService.getOrderStatistics());
    }

    /**
     * 生成订单号
     */
    @GetMapping("/generate-order-no")
    @SaCheckPermission("purchase:order:add")
    public Result<String> generateOrderNo() {
        return Result.success(purchaseOrderService.generateOrderNo());
    }

    /**
     * 复制订单
     */
    @PostMapping("/copy/{orderId}")
    @Log(module = "采购订单管理", businessType = BusinessType.INSERT)
    @SaCheckPermission("purchase:order:add")
    public Result<Long> copy(@PathVariable Long orderId) {
        return Result.success(purchaseOrderService.copyOrder(orderId));
    }

    /**
     * 导出采购订单列表
     */
    @PostMapping("/export")
    @Log(module = "采购订单管理", businessType = BusinessType.EXPORT)
    @SaCheckPermission("purchase:order:export")
    public Result<String> export(@RequestBody PurchaseOrderQueryDTO queryVO) {
        return Result.success(purchaseOrderService.exportOrderList(queryVO));
    }

    /**
     * 导出采购订单详情
     */
    @PostMapping("/export/{orderId}")
    @Log(module = "采购订单管理", businessType = BusinessType.EXPORT)
    @SaCheckPermission("purchase:order:export")
    public Result<String> exportDetail(@PathVariable Long orderId) {
        return Result.success(purchaseOrderService.exportOrderDetail(orderId));
    }
}
