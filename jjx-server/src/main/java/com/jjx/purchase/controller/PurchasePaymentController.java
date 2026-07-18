package com.jjx.purchase.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.common.core.result.Result;
import com.jjx.framework.common.controller.BaseController;
import com.jjx.purchase.domain.dto.PurchasePaymentDTO;
import com.jjx.purchase.domain.entity.PurchasePayment;
import com.jjx.purchase.domain.vo.PurchaseOrderVO;
import com.jjx.purchase.service.IPurchaseOrderService;
import com.jjx.purchase.service.IPurchasePaymentService;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.annotation.Log;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 采购付款Controller
 */
@Slf4j
@RestController
@RequestMapping("/purchase/payment")
@RequiredArgsConstructor
public class PurchasePaymentController extends BaseController {

    private final IPurchasePaymentService paymentService;
    private final IPurchaseOrderService purchaseOrderService;

    /**
     * 查询采购付款列表
     */
    @GetMapping("/list")
    @SaCheckPermission("purchase:payment:view")
    public Result<?> list(PurchasePaymentDTO dto) {
        return Result.success(paymentService.selectPaymentList(dto));
    }

    /**
     * 查询采购付款详细
     */
    @GetMapping("/{paymentId}")
    @SaCheckPermission("purchase:payment:view")
    public Result<PurchasePayment> getInfo(@PathVariable Long paymentId) {
        return Result.success(paymentService.selectPaymentById(paymentId));
    }

    /**
     * 新增采购付款
     */
    @PostMapping
    @Log(module = "采购付款管理", businessType = BusinessType.INSERT)
    @SaCheckPermission("purchase:payment:add")
    public Result<Void> add(@Valid @RequestBody PurchasePaymentDTO dto) {
        paymentService.insertPayment(dto);
        return Result.success();
    }

    /**
     * 修改采购付款
     */
    @PutMapping
    @Log(module = "采购付款管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("purchase:payment:edit")
    public Result<Void> edit(@Valid @RequestBody PurchasePaymentDTO dto) {
        paymentService.updatePayment(dto);
        return Result.success();
    }

    /**
     * 删除采购付款
     */
    @DeleteMapping("/{paymentIds}")
    @Log(module = "采购付款管理", businessType = BusinessType.DELETE)
    @SaCheckPermission("purchase:payment:delete")
    public Result<Void> remove(@PathVariable Long[] paymentIds) {
        paymentService.deletePaymentByIds(paymentIds);
        return Result.success();
    }

    /**
     * 导出采购付款列表
     */
    @GetMapping("/export")
    @SaCheckPermission("purchase:payment:export")
    public Result<String> export(PurchasePaymentDTO dto) {
        return Result.success(paymentService.exportPaymentList(dto));
    }

    /**
     * 审批付款
     */
    @PutMapping("/approve/{paymentId}")
    @Log(module = "采购付款管理", businessType = BusinessType.APPROVE)
    @SaCheckPermission("purchase:payment:approve")
    public Result<Void> approve(@PathVariable Long paymentId,
                                @RequestParam String approvalStatus,
                                @RequestParam String approverName,
                                @RequestParam(required = false) String approvalComment) {
        paymentService.approvePayment(paymentId, approvalStatus, approverName, approvalComment);
        return Result.success();
    }

    /**
     * 确认付款
     */
    @PostMapping("/confirm")
    @Log(module = "采购付款管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("purchase:payment:edit")
    public Result<Void> confirm(@Valid PurchasePaymentDTO dto) {
        paymentService.confirmPayment(dto);
        return Result.success();
    }

    /**
     * 上传凭证
     */
    @PostMapping("/upload-voucher")
    @Log(module = "采购付款管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("purchase:payment:edit")
    public Result<Void> uploadVoucher(@Valid PurchasePaymentDTO dto) {
        paymentService.confirmPayment(dto);
        return Result.success();
    }

    /**
     * 查询待付款的订单列表
     */
    @GetMapping("/pending-orders")
    @SaCheckPermission("purchase:payment:view")
    public Result<List<PurchaseOrderVO>> pendingOrders() {
        return Result.success(purchaseOrderService.selectPendingPaymentOrders());
    }

    /**
     * 根据订单ID查询付款记录
     */
    @GetMapping("/order/{orderId}")
    @SaCheckPermission("purchase:payment:view")
    public Result<List<PurchasePayment>> getByOrder(@PathVariable Long orderId) {
        return Result.success(paymentService.selectByOrderId(orderId));
    }

    /**
     * 根据供应商ID查询付款记录
     */
    @GetMapping("/supplier/{supplierId}")
    @SaCheckPermission("purchase:payment:view")
    public Result<List<PurchasePayment>> getBySupplier(@PathVariable Long supplierId) {
        return Result.success(paymentService.selectBySupplierId(supplierId));
    }

    /**
     * 查询待审批的付款列表
     */
    @GetMapping("/pending-approval")
    @SaCheckPermission("purchase:payment:view")
    public Result<List<PurchasePayment>> pendingApproval() {
        return Result.success(paymentService.selectPendingApproval());
    }

    /**
     * 查询已审批的付款列表
     */
    @GetMapping("/approved")
    @SaCheckPermission("purchase:payment:view")
    public Result<List<PurchasePayment>> approved() {
        return Result.success(paymentService.selectApproved());
    }

    /**
     * 查询今日付款记录
     */
    @GetMapping("/today")
    @SaCheckPermission("purchase:payment:view")
    public Result<List<PurchasePayment>> today() {
        return Result.success(paymentService.selectToday());
    }

    /**
     * 查询本周付款记录
     */
    @GetMapping("/week")
    @SaCheckPermission("purchase:payment:view")
    public Result<List<PurchasePayment>> week() {
        return Result.success(paymentService.selectWeek());
    }

    /**
     * 查询本月付款记录
     */
    @GetMapping("/month")
    @SaCheckPermission("purchase:payment:view")
    public Result<List<PurchasePayment>> month() {
        return Result.success(paymentService.selectMonth());
    }

    /**
     * 获取付款统计信息
     */
    @GetMapping("/statistics")
    @SaCheckPermission("purchase:payment:view")
    public Result<Map<String, Object>> statistics() {
        return Result.success(paymentService.getPaymentStatistics());
    }

    /**
     * 批量付款
     */
    @PostMapping("/batch")
    @Log(module = "采购付款管理", businessType = BusinessType.INSERT)
    @SaCheckPermission("purchase:payment:add")
    public Result<Void> batchPayment(@RequestBody List<PurchasePaymentDTO> batchData) {
        for (PurchasePaymentDTO dto : batchData) {
            paymentService.insertPayment(dto);
        }
        return Result.success();
    }

    /**
     * 批量审批
     */
    @PostMapping("/batch-approve")
    @Log(module = "采购付款管理", businessType = BusinessType.APPROVE)
    @SaCheckPermission("purchase:payment:approve")
    public Result<Void> batchApprove(@RequestBody List<Map<String, Object>> batchData) {
        for (Map<String, Object> data : batchData) {
            Long paymentId = Long.valueOf(data.get("paymentId").toString());
            String approvalStatus = (String) data.get("approvalStatus");
            String approverName = (String) data.get("approverName");
            String approvalComment = (String) data.get("approvalComment");
            paymentService.approvePayment(paymentId, approvalStatus, approverName, approvalComment);
        }
        return Result.success();
    }

    /**
     * 导入付款数据
     */
    @PostMapping("/import")
    @Log(module = "采购付款管理", businessType = BusinessType.IMPORT)
    @SaCheckPermission("purchase:payment:import")
    public Result<Void> importPayment(@RequestBody List<PurchasePaymentDTO> importData) {
        for (PurchasePaymentDTO dto : importData) {
            paymentService.insertPayment(dto);
        }
        return Result.success();
    }

    /**
     * 下载付款导入模板
     */
    @GetMapping("/import-template")
    @SaCheckPermission("purchase:payment:import")
    public Result<String> importTemplate() {
        return Result.success("导入模板生成功能待实现");
    }

    /**
     * 检查付款单号是否唯一
     */
    @GetMapping("/check-payment-no-unique")
    @SaCheckPermission("purchase:payment:view")
    public Result<Boolean> checkPaymentNoUnique(@RequestParam String paymentNo) {
        return Result.success(!paymentService.checkPaymentNoUnique(paymentNo));
    }

    /**
     * 生成付款单号
     */
    @GetMapping("/generate-payment-no")
    @SaCheckPermission("purchase:payment:add")
    public Result<String> generatePaymentNo() {
        return Result.success(paymentService.generatePaymentNo());
    }

    /**
     * 获取付款提醒
     */
    @GetMapping("/reminders")
    @SaCheckPermission("purchase:payment:view")
    public Result<List<Map<String, Object>>> reminders() {
        List<PurchasePayment> pendingPayments = paymentService.selectPendingApproval();
        List<Map<String, Object>> reminders = pendingPayments.stream()
                .map(p -> Map.<String, Object>of(
                        "paymentId", p.getPaymentId(),
                        "paymentNo", p.getPaymentNo(),
                        "paymentAmount", p.getPaymentAmount(),
                        "paymentDate", p.getPaymentDate(),
                        "message", "付款单 " + p.getPaymentNo() + " 待审批"
                ))
                .collect(Collectors.toList());
        return Result.success(reminders);
    }

    /**
     * 获取逾期付款列表
     */
    @GetMapping("/overdue")
    @SaCheckPermission("purchase:payment:view")
    public Result<List<PurchaseOrderVO>> overdue() {
        List<PurchaseOrderVO> orders = purchaseOrderService.selectPendingPaymentOrders();
        return Result.success(orders);
    }

    /**
     * 获取付款趋势分析
     */
    @GetMapping("/trend-analysis")
    @SaCheckPermission("purchase:payment:view")
    public Result<Map<String, Object>> trendAnalysis() {
        return Result.success(Map.of("message", "趋势分析功能待实现"));
    }

    /**
     * 获取供应商付款分析
     */
    @GetMapping("/supplier-analysis")
    @SaCheckPermission("purchase:payment:view")
    public Result<Map<String, Object>> supplierAnalysis(@RequestParam(required = false) Long supplierId) {
        return Result.success(Map.of("message", "供应商付款分析功能待实现"));
    }
}
