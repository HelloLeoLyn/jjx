package com.jjx.inventory.controller;

import com.jjx.common.constant.LogActions;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jjx.common.core.result.Result;
import com.jjx.inventory.dto.query.InboundQueryDTO;
import com.jjx.inventory.dto.query.IqcPendingQueryDTO;
import com.jjx.inventory.dto.vo.InboundVO;
import com.jjx.inventory.dto.vo.IqcPendingVO;
import com.jjx.inventory.service.InventoryInboundService;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.jjx.inventory.dto.save.InboundInspectionSubmitDTO;
import com.jjx.inventory.dto.save.InboundInspectionReviewDTO;
import com.jjx.inventory.dto.save.IqcQuarantineActionDTO;
import com.jjx.inventory.domain.InventoryIqcQuarantine;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.annotation.Log;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 入库管理Controller
 */
@RestController
@RequestMapping("/inventory/inbound")
@RequiredArgsConstructor
@Tag(name = "入库管理", description = "入库管理相关接口")
public class InventoryInboundController {

    private final InventoryInboundService inboundService;

    @GetMapping("/list")
    @Operation(summary = "分页查询入库单")
    @SaCheckPermission("inventory:inbound:view")
    public Result<IPage<InboundVO>> list(InboundQueryDTO query) {
        return Result.success(inboundService.page(query));
    }

    @GetMapping("/iqc-pending")
    @Operation(summary = "分页查询 IQC 待检采购收货单")
    @SaCheckPermission("inventory:inbound:view")
    public Result<IPage<IqcPendingVO>> iqcPending(IqcPendingQueryDTO query) {
        return Result.success(inboundService.pageIqcPending(query));
    }

    @GetMapping("/{inboundId}")
    @Operation(summary = "获取入库单详情")
    @SaCheckPermission("inventory:inbound:view")
    public Result<InboundVO> getById(@PathVariable Long inboundId) {
        return Result.success(inboundService.getDetail(inboundId));
    }

    @PostMapping("/create")
    @Operation(summary = "创建入库单")
    @Log(module = "入库管理", businessType = BusinessType.INSERT, bizType = "'inbound'", bizId = "#result.data", action = LogActions.INBOUND_CREATE)
    @SaCheckPermission("inventory:inbound:add")
    public Result<Long> create(@RequestBody Map<String, Object> params) {
        return Result.success(inboundService.create(params));
    }

    @PostMapping("/confirm/{inboundId}")
    @Operation(summary = "确认入库")
    @Log(module = "入库管理", businessType = BusinessType.UPDATE, bizType = "'inbound'", bizId = "#inboundId", bizStatus = "T(com.jjx.inventory.enums.InventoryOrderStatusEnum).COMPLETED.getLabel()", action = LogActions.INBOUND_CONFIRM)
    @SaCheckPermission("inventory:inbound:edit")
    public Result<Boolean> confirm(@PathVariable Long inboundId,
                                   @RequestParam Long operatorId,
                                   @RequestParam String operatorName) {
        return Result.success(inboundService.confirm(inboundId, operatorId, operatorName));
    }

    @PostMapping("/cancel/{inboundId}")
    @Operation(summary = "取消入库单")
    @Log(module = "入库管理", businessType = BusinessType.UPDATE, bizType = "'inbound'", bizId = "#inboundId", bizStatus = "T(com.jjx.inventory.enums.InventoryOrderStatusEnum).CANCELLED.getLabel()", action = LogActions.INBOUND_CANCEL)
    @SaCheckPermission("inventory:inbound:edit")
    public Result<Boolean> cancel(@PathVariable Long inboundId,
                                  @RequestParam String reason) {
        return Result.success(inboundService.cancel(inboundId, reason));
    }

    @PostMapping("/submit-approve/{inboundId}")
    @Operation(summary = "提交审批")
    @Log(module = "入库管理", businessType = BusinessType.UPDATE, bizType = "'inbound'", bizId = "#inboundId", bizStatus = "T(com.jjx.inventory.enums.InventoryOrderStatusEnum).PENDING.getLabel()", action = LogActions.INBOUND_SUBMIT)
    @SaCheckPermission(value = {"inventory:inbound:edit", "quality:inspector"}, mode = SaMode.OR)
    public Result<Boolean> submitApprove(@PathVariable Long inboundId,
                                         @RequestBody(required = false) InboundInspectionSubmitDTO inspection) {
        return Result.success(inboundService.submitApprove(inboundId, inspection));
    }

    @PostMapping("/inspection-item/{itemId}/approve")
    @Operation(summary = "单项 IQC 审核通过（不执行库存过账）")
    @SaCheckPermission("inventory:inbound:approve")
    public Result<Boolean> approveInspectionItem(@PathVariable Long itemId,
                                                  @RequestBody InboundInspectionReviewDTO review) {
        return Result.success(inboundService.approveInspectionItem(itemId, review));
    }

    @PostMapping("/inspection-item/{itemId}/reject")
    @Operation(summary = "单项 IQC 审核驳回")
    @SaCheckPermission("inventory:inbound:approve")
    public Result<Boolean> rejectInspectionItem(@PathVariable Long itemId,
                                                 @RequestBody InboundInspectionReviewDTO review) {
        return Result.success(inboundService.rejectInspectionItem(itemId, review));
    }

    @PostMapping("/inspection-item/{itemId}/reinspect")
    @Operation(summary = "单项 IQC 发起复检")
    @SaCheckPermission(value = {"inventory:inbound:edit", "quality:inspector"}, mode = SaMode.OR)
    public Result<Long> reinspectItem(@PathVariable Long itemId) {
        return Result.success(inboundService.reinspectItem(itemId));
    }

    @GetMapping("/{inboundId}/iqc-quarantine")
    @Operation(summary = "查询 IQC 隔离台账")
    @SaCheckPermission("inventory:inbound:view")
    public Result<List<InventoryIqcQuarantine>> listQuarantine(@PathVariable Long inboundId) {
        return Result.success(inboundService.listQuarantine(inboundId));
    }

    @PostMapping("/iqc-quarantine/{quarantineId}/action")
    @Operation(summary = "执行 IQC 隔离品处置")
    @SaCheckPermission("inventory:inbound:edit")
    public Result<Boolean> handleQuarantine(@PathVariable Long quarantineId,
                                            @RequestBody IqcQuarantineActionDTO action) {
        return Result.success(inboundService.handleQuarantine(quarantineId, action));
    }

    @GetMapping("/{inboundId}/iqc-disposition-orders")
    @Operation(summary = "查询 IQC 隔离处置单")
    @SaCheckPermission("inventory:inbound:view")
    public Result<List<com.jjx.inventory.domain.InventoryIqcDispositionOrder>> listDispositionOrders(@PathVariable Long inboundId) {
        return Result.success(inboundService.listDispositionOrders(inboundId));
    }

    @GetMapping("/iqc-quarantine/list")
    @Operation(summary = "查询全部 IQC 隔离台账")
    @SaCheckPermission("inventory:inbound:view")
    public Result<List<InventoryIqcQuarantine>> listAllQuarantine(@RequestParam(required = false) String status) {
        return Result.success(inboundService.listAllQuarantine(status));
    }

    @GetMapping("/iqc-disposition-orders/list")
    @Operation(summary = "查询全部 IQC 处置单")
    @SaCheckPermission("inventory:inbound:view")
    public Result<List<com.jjx.inventory.domain.InventoryIqcDispositionOrder>> listAllDispositionOrders(@RequestParam(required = false) String action) {
        return Result.success(inboundService.listAllDispositionOrders(action));
    }

    @GetMapping("/iqc-disposition-orders/{dispositionId}")
    @Operation(summary = "查询 IQC 处置单详情")
    @SaCheckPermission("inventory:inbound:view")
    public Result<com.jjx.inventory.domain.InventoryIqcDispositionOrder> getDispositionOrder(@PathVariable Long dispositionId) {
        return Result.success(inboundService.getDispositionOrder(dispositionId));
    }

    @GetMapping("/{inboundId}/iqc-return-orders")
    @Operation(summary = "查询 IQC 采购退货单")
    @SaCheckPermission("inventory:inbound:view")
    public Result<List<com.jjx.inventory.domain.InventoryIqcReturnOrder>> listIqcReturnOrders(@PathVariable Long inboundId) {
        return Result.success(inboundService.listIqcReturnOrders(inboundId));
    }

    @GetMapping("/{inboundId}/iqc-rework-orders")
    @Operation(summary = "查询 IQC 供应商返工单")
    @SaCheckPermission("inventory:inbound:view")
    public Result<List<com.jjx.inventory.domain.InventoryIqcReworkOrder>> listIqcReworkOrders(@PathVariable Long inboundId) {
        return Result.success(inboundService.listIqcReworkOrders(inboundId));
    }

    @GetMapping("/{inboundId}/iqc-scrap-orders")
    @Operation(summary = "查询 IQC 报废审批单")
    @SaCheckPermission("inventory:inbound:view")
    public Result<List<com.jjx.inventory.domain.InventoryIqcScrapOrder>> listIqcScrapOrders(@PathVariable Long inboundId) {
        return Result.success(inboundService.listIqcScrapOrders(inboundId));
    }

    @PostMapping("/iqc-scrap-orders/{scrapId}/approve")
    @Operation(summary = "审批 IQC 报废单")
    @SaCheckPermission("inventory:inbound:approve")
    public Result<Boolean> approveIqcScrap(@PathVariable Long scrapId,
                                           @RequestBody com.jjx.inventory.dto.save.IqcScrapApproveDTO approval) {
        return Result.success(inboundService.approveIqcScrap(scrapId, approval));
    }

    @PostMapping("/iqc-rework-orders/{reworkId}/complete")
    @Operation(summary = "完成 IQC 返工并发起复检")
    @SaCheckPermission("inventory:inbound:edit")
    public Result<Long> completeIqcRework(@PathVariable Long reworkId) {
        return Result.success(inboundService.completeIqcRework(reworkId));
    }

    @PostMapping("/approve/{inboundId}")
    @Operation(summary = "审批通过")
    @Log(module = "入库管理", businessType = BusinessType.APPROVE, bizType = "'inbound'", bizId = "#inboundId", bizStatus = "T(com.jjx.inventory.enums.InventoryOrderStatusEnum).APPROVED.getLabel()", action = LogActions.INBOUND_APPROVE)
    @SaCheckPermission("inventory:inbound:approve")
    public Result<Boolean> approve(@PathVariable Long inboundId,
                                   @RequestParam Long approverId,
                                   @RequestParam String approverName,
                                   @RequestParam(required = false) String remark) {
        return Result.success(inboundService.approve(inboundId, approverId, approverName, remark));
    }

    @PostMapping("/reject/{inboundId}")
    @Operation(summary = "审批驳回")
    @Log(module = "入库管理", businessType = BusinessType.APPROVE, bizType = "'inbound'", bizId = "#inboundId", bizStatus = "T(com.jjx.inventory.enums.InventoryOrderStatusEnum).REJECTED.getLabel()", action = LogActions.INBOUND_REJECT)
    @SaCheckPermission("inventory:inbound:approve")
    public Result<Boolean> reject(@PathVariable Long inboundId,
                                  @RequestParam Long approverId,
                                  @RequestParam String approverName,
                                  @RequestParam String remark) {
        return Result.success(inboundService.reject(inboundId, approverId, approverName, remark));
    }

    @PostMapping("/create-from-purchase/{purchaseOrderId}")
    @Operation(summary = "从采购订单创建入库单")
    @Log(module = "入库管理", businessType = BusinessType.INSERT, bizType = "'inbound'", bizId = "#purchaseOrderId", action = LogActions.INBOUND_FROM_PURCHASE)
    @SaCheckPermission("inventory:inbound:add")
    public Result<Long> createFromPurchase(@PathVariable Long purchaseOrderId) {
        return Result.success(inboundService.createFromPurchase(purchaseOrderId));
    }

    @PostMapping("/create-from-production/{workOrderId}")
    @Operation(summary = "从生产工单创建入库单")
    @Log(module = "入库管理", businessType = BusinessType.INSERT, bizType = "'inbound'", bizId = "#workOrderId", action = LogActions.INBOUND_FROM_PRODUCTION)
    @SaCheckPermission("inventory:inbound:add")
    public Result<Long> createFromProduction(@PathVariable Long workOrderId) {
        return Result.success(inboundService.createFromProduction(workOrderId));
    }

    @GetMapping("/pending-approval")
    @Operation(summary = "查询待审批的入库单")
    @SaCheckPermission("inventory:inbound:view")
    public Result<List<InboundVO>> getPendingApproval() {
        return Result.success(inboundService.getPendingApproval());
    }

    @GetMapping("/date-range")
    @Operation(summary = "查询日期范围内的入库单")
    @SaCheckPermission("inventory:inbound:view")
    public Result<List<InboundVO>> getByDateRange(@RequestParam String startDate,
                                                  @RequestParam String endDate) {
        return Result.success(inboundService.getByDateRange(startDate, endDate));
    }

    @GetMapping("/source")
    @Operation(summary = "根据来源单据查询入库单")
    @SaCheckPermission("inventory:inbound:view")
    public Result<InboundVO> getBySource(@RequestParam String sourceType,
                                         @RequestParam Long sourceId) {
        return Result.success(inboundService.getBySource(sourceType, sourceId));
    }

    @PostMapping("/update-status/{inboundId}")
    @Operation(summary = "更新入库单状态")
    @Log(module = "入库管理", businessType = BusinessType.UPDATE, bizType = "'inbound'", bizId = "#inboundId", bizStatus = "T(com.jjx.inventory.enums.InventoryOrderStatusEnum).getByValue(#status)?.label", action = LogActions.INBOUND_UPDATE_STATUS)
    @SaCheckPermission("inventory:inbound:edit")
    public Result<Boolean> updateStatus(@PathVariable Long inboundId,
                                        @RequestParam Integer status) {
        return Result.success(inboundService.updateStatus(inboundId, status));
    }

    @GetMapping("/dashboard")
    @Operation(summary = "获取入库仪表板数据")
    @SaCheckPermission("inventory:inbound:view")
    public Result<Map<String, Object>> getDashboard() {
        // 这里可以添加一些统计信息，比如今日入库数量、金额等
        return Result.success(Map.of(
                "todayInboundCount", 0,
                "todayInboundAmount", 0,
                "pendingApprovalCount", 0,
                "pendingConfirmCount", 0
        ));
    }
}
