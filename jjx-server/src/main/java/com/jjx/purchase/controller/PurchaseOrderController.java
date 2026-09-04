package com.jjx.purchase.controller;

import com.jjx.common.constant.LogActions;
import com.jjx.common.core.page.PageResult;
import com.jjx.common.core.result.Result;
import com.jjx.common.exception.BusinessException;
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
import io.swagger.v3.oas.annotations.Operation;
import com.jjx.system.annotation.Log;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

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
     * 获取采购订单总数
     */
    @GetMapping("/count")
    public Result<Long> count() {
        return Result.success(purchaseOrderService.count());
    }

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
     * 查询物料在途采购量（2026-08-18 P1-B：采购计划工作台手动添加物料时防重复下单用，含草稿单）
     */
    @Operation(summary = "查询物料在途采购量")
    @SaCheckPermission("purchase:plan:view")
    @GetMapping("/in-transit")
    public Result<Map<Long, BigDecimal>> inTransit(@RequestParam List<Long> materialIds) {
        return Result.success(purchaseOrderService.getInTransitByMaterials(materialIds));
    }

    /**
     * 新增采购订单
     */
    @PostMapping
    @Log(module = "采购订单管理", businessType = BusinessType.INSERT, bizType = "'purchase_order'", bizId = "#orderDTO.orderId", bizStatus = "T(com.jjx.common.enums.ApproveStatusEnum).DRAFT.getLabel()", action = LogActions.PUR_ORDER_CREATE)
    @SaCheckPermission("purchase:order:add")
    public Result<Void> add(@RequestBody PurchaseOrderDTO orderDTO) {
        purchaseOrderService.insertOrder(orderDTO);
        return Result.success();
    }

    /**
     * 修改采购订单
     */
    @PutMapping
    @Log(module = "采购订单管理", businessType = BusinessType.UPDATE, bizType = "'purchase_order'", bizId = "#orderDTO.orderId", action = LogActions.PUR_ORDER_EDIT)
    @SaCheckPermission("purchase:order:edit")
    public Result<Void> edit(@Valid @RequestBody PurchaseOrderDTO orderDTO) {
        purchaseOrderService.updateOrder(orderDTO);
        return Result.success();
    }

    /**
     * 取消采购订单
     */
    @PutMapping("/cancel/{orderId}")
    @Log(module = "采购订单管理", businessType = BusinessType.UPDATE, bizType = "'purchase_order'", bizId = "#orderId", bizStatus = "T(com.jjx.common.enums.ApproveStatusEnum).CANCELLED.getLabel()", detail = "#attachmentIds", action = LogActions.PUR_ORDER_CANCEL)
    @SaCheckPermission("purchase:order:edit")
    public Result<Void> cancel(@PathVariable Long orderId,
                               // 仅供 @Log SpEL 取值，业务方法无需使用
                               @RequestParam(required = false) String attachmentIds) {
        purchaseOrderService.cancelOrder(orderId);
        return Result.success();
    }

    /**
     * 采购退货
     */
    @Operation(summary = "采购退货")
    @Log(module = "采购订单管理", businessType = BusinessType.UPDATE, bizType = "'purchase_order'", bizId = "#orderId", action = LogActions.PUR_ORDER_RETURN)
    @SaCheckPermission("purchase:order:edit")
    @PostMapping("/return/{orderId}")
    public Result<Void> returnGoods(@PathVariable Long orderId,
                                    @RequestParam String reason,
                                    @RequestParam(defaultValue = "0") Long materialId,
                                    @RequestParam(defaultValue = "0") Integer quantity) {
        purchaseOrderService.returnGoods(orderId, reason, materialId, quantity);
        return Result.success();
    }

    /**
     * 提交审批
     */
    @PutMapping("/submit/{orderId}")
    @Log(module = "采购订单管理", businessType = BusinessType.UPDATE, bizType = "'purchase_order'", bizId = "#orderId", bizStatus = "T(com.jjx.common.enums.ApproveStatusEnum).PENDING.getLabel()", action = LogActions.PUR_ORDER_SUBMIT)
    @SaCheckPermission("purchase:order:edit")
    public Result<Void> submit(@PathVariable Long orderId) {
        purchaseOrderService.submitOrder(orderId);
        return Result.success();
    }

    /**
     * 批量提交审批
     */
    @PutMapping("/batch-submit")
    @Log(module = "采购订单管理", businessType = BusinessType.UPDATE, bizType = "'purchase_order'", bizId = "#orderIds[0]", bizStatus = "T(com.jjx.common.enums.ApproveStatusEnum).PENDING.getLabel()", action = LogActions.PUR_ORDER_BATCH_SUBMIT)
    @SaCheckPermission("purchase:order:edit")
    public Result<Void> batchSubmit(@RequestBody List<Long> orderIds) {
        purchaseOrderService.batchSubmitOrders(orderIds);
        return Result.success();
    }

    /**
     * 审批订单
     */
    @PutMapping("/approve")
    @Log(module = "采购订单管理", businessType = BusinessType.APPROVE, bizType = "'purchase_order'", bizId = "#dto.orderId", bizStatus = "#result.data.label", detail = "#attachmentIds", action = LogActions.PUR_ORDER_APPROVE)
    @SaCheckPermission("purchase:order:approve")
    public Result<com.jjx.common.enums.ApproveStatusEnum> approve(@Valid @RequestBody PurchaseOrderApprovalDTO dto,
                                // 仅供 @Log SpEL 取值，业务方法无需使用
                                @RequestParam(required = false) String attachmentIds) {
        com.jjx.common.enums.ApproveStatusEnum status = purchaseOrderService.approveOrder(dto);
        return status != null ? Result.success(status) : Result.error();
    }

    /**
     * 更新订单审批状态
     */
    @PutMapping("/status")
    @Log(module = "采购订单管理", businessType = BusinessType.UPDATE, bizType = "'purchase_order'", bizId = "#orderId", bizStatus = "T(com.jjx.common.enums.ApproveStatusEnum).getByValue(#approvalStatus)?.label", action = LogActions.PUR_ORDER_STATUS)
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
    @Log(module = "采购订单管理", businessType = BusinessType.UPDATE, bizType = "'purchase_order'", bizId = "#orderId", bizStatus = "T(com.jjx.common.enums.ApproveStatusEnum).APPROVED.getLabel()", action = LogActions.PUR_ORDER_RECEIVE)
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
    @Log(module = "采购订单管理", businessType = BusinessType.UPDATE, bizType = "'purchase_order'", bizId = "#orderId", bizStatus = "T(com.jjx.purchase.domain.enums.ReceiptStatusEnum).getByValue(#receiptStatus)?.label", action = LogActions.PUR_ORDER_RECEIPT_STATUS)
    @SaCheckPermission("purchase:order:edit")
    public Result<Void> updateReceiptStatus(@RequestParam Long orderId, @RequestParam Integer receiptStatus) {
        purchaseOrderService.updateReceiptStatus(orderId, receiptStatus);
        return Result.success();
    }

    /**
     * 更新付款信息
     */
    @PutMapping("/payment")
    @Log(module = "采购订单管理", businessType = BusinessType.UPDATE, bizType = "'purchase_order'", bizId = "#result.data", bizStatus = "T(com.jjx.purchase.domain.enums.PurchasePaymentStatusEnum).getByValue(#paymentStatus)?.label", action = LogActions.PUR_ORDER_PAYMENT)
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

    // ==================== DEV-664 采购计划 ====================

    /**
     * 确认计划单转正式采购单（计划单体系已弃用，2026-08-18：前端无入口，待后续清理或重构）
     *
     * @deprecated 计划单体系（plan_status=1）已弃用
     */
    @Deprecated
    @Operation(summary = "确认计划单转正式采购单（已弃用）")
    @Log(module = "采购订单管理", businessType = BusinessType.UPDATE, bizType = "'purchase_order'", bizId = "#orderId", action = LogActions.PUR_ORDER_CONFIRM_PLAN)
    @SaCheckPermission("purchase:plan:confirm")
    @PutMapping("/{orderId}/confirm-plan")
    public Result<Void> confirmPlan(@PathVariable Long orderId,
                                    @RequestParam Long supplierId,
                                    @RequestParam String supplierName) {
        purchaseOrderService.confirmPlan(orderId, supplierId, supplierName);
        return Result.success();
    }

    /**
     * 获取采购计划建议（安全库存预警 + 订单缺料预警）
     */
    @Operation(summary = "获取采购计划建议")
    @SaCheckPermission("purchase:plan:view")
    @GetMapping("/plan-suggestions")
    public Result<List<Map<String, Object>>> planSuggestions() {
        return Result.success(purchaseOrderService.getPlanSuggestions());
    }

    @Operation(summary = "记录采购计划打印")
    @Log(module = "采购计划", businessType = BusinessType.OTHER, bizType = "'plan_print'", bizId = "'suggestions'", action = LogActions.PUR_PLAN_PRINT_LOG)
    @SaCheckPermission("purchase:plan:view")
    @PostMapping("/plan-suggestions/print-log")
    public Result<Void> planPrintLog() { return Result.success(); }

    /**
     * 092：缺料预警一键生成采购计划单（已弃用，2026-08-18：前端无调用）
     *
     * @deprecated 计划单体系已弃用
     */
    @Deprecated
    @Operation(summary = "缺料预警一键生成采购计划单（已弃用）")
    @Log(module = "采购订单管理", businessType = BusinessType.INSERT, bizType = "'purchase_order'", action = LogActions.PUR_PLAN_FROM_SUGGESTIONS)
    @SaCheckPermission("purchase:plan:add")
    @PostMapping("/create-plan-from-suggestions")
    public Result<Long> createPlanFromSuggestions() {
        return Result.success(purchaseOrderService.createPlanFromSuggestions());
    }

    /**
     * DEV-996：预警页选中/单条预警一键转采购（生成采购计划单 + 自动回写预警）
     * 已弃用（2026-08-18）：预警页转采购入口已移除，采购侧统一走采购计划工作台
     *
     * @deprecated 计划单体系已弃用
     */
    @Deprecated
    @Operation(summary = "选中预警一键生成采购计划单（已弃用）")
    @Log(module = "采购订单管理", businessType = BusinessType.INSERT, bizType = "'purchase_order'", action = LogActions.PUR_PLAN_FROM_ALERTS)
    @SaCheckPermission("purchase:plan:add")
    @PostMapping("/create-plan-from-alerts")
    public Result<Long> createPlanFromAlerts(@RequestBody java.util.List<Long> alertIds) {
        return Result.success(purchaseOrderService.createPlanFromAlerts(alertIds));
    }

    /**
     * 复制订单
     */
    @PostMapping("/copy/{orderId}")
    @Log(module = "采购订单管理", businessType = BusinessType.INSERT, bizType = "'purchase_order'", bizId = "#orderId", action = LogActions.PUR_ORDER_COPY)
    @SaCheckPermission("purchase:order:add")
    public Result<Long> copy(@PathVariable Long orderId) {
        return Result.success(purchaseOrderService.copyOrder(orderId));
    }

    /**
     * 导出采购订单列表
     */
    @PostMapping("/export")
    @Log(module = "采购订单管理", businessType = BusinessType.EXPORT, bizType = "'purchase_order'", bizId = "'export'", action = LogActions.PUR_ORDER_EXPORT)
    @SaCheckPermission("purchase:order:export")
    public Result<String> export(@RequestBody PurchaseOrderQueryDTO queryVO) {
        return Result.success(purchaseOrderService.exportOrderList(queryVO));
    }

    /**
     * 导出采购订单详情
     */
    @PostMapping("/export/{orderId}")
    @Log(module = "采购订单管理", businessType = BusinessType.EXPORT, bizType = "'purchase_order'", bizId = "#orderId", action = LogActions.PUR_ORDER_EXPORT_DETAIL)
    @SaCheckPermission("purchase:order:export")
    public Result<String> exportDetail(@PathVariable Long orderId) {
        return Result.success(purchaseOrderService.exportOrderDetail(orderId));
    }

    /**
     * 删除采购订单
     */
    @DeleteMapping("/{orderId}")
    @Log(module = "采购订单管理", businessType = BusinessType.DELETE, bizType = "'purchase_order'", bizId = "#orderId", action = LogActions.PUR_ORDER_DELETE)
    @SaCheckPermission("purchase:order:delete")
    public Result<Void> deleteOrder(@PathVariable Long orderId) {
        com.jjx.purchase.domain.entity.PurchaseOrder order = purchaseOrderService.getById(orderId);
        if (order == null) {
            throw new BusinessException("采购订单不存在");
        }
        purchaseOrderService.removeById(orderId);
        return Result.success();
    }
}
