package com.jjx.inventory.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jjx.common.core.result.Result;
import com.jjx.inventory.dto.query.InboundQueryDTO;
import com.jjx.inventory.dto.vo.InboundVO;
import com.jjx.inventory.service.InventoryInboundService;
import cn.dev33.satoken.annotation.SaCheckPermission;
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

    @GetMapping("/{inboundId}")
    @Operation(summary = "获取入库单详情")
    @SaCheckPermission("inventory:inbound:view")
    public Result<InboundVO> getById(@PathVariable Long inboundId) {
        return Result.success(inboundService.getDetail(inboundId));
    }

    @PostMapping("/create")
    @Operation(summary = "创建入库单")
    @Log(module = "入库管理", businessType = BusinessType.INSERT)
    @SaCheckPermission("inventory:inbound:add")
    public Result<Long> create(@RequestBody Map<String, Object> params) {
        return Result.success(inboundService.create(params));
    }

    @PostMapping("/confirm/{inboundId}")
    @Operation(summary = "确认入库")
    @Log(module = "入库管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("inventory:inbound:edit")
    public Result<Boolean> confirm(@PathVariable Long inboundId,
                                   @RequestParam Long operatorId,
                                   @RequestParam String operatorName) {
        return Result.success(inboundService.confirm(inboundId, operatorId, operatorName));
    }

    @PostMapping("/cancel/{inboundId}")
    @Operation(summary = "取消入库单")
    @Log(module = "入库管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("inventory:inbound:edit")
    public Result<Boolean> cancel(@PathVariable Long inboundId,
                                  @RequestParam String reason) {
        return Result.success(inboundService.cancel(inboundId, reason));
    }

    @PostMapping("/submit-approve/{inboundId}")
    @Operation(summary = "提交审批")
    @Log(module = "入库管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("inventory:inbound:edit")
    public Result<Boolean> submitApprove(@PathVariable Long inboundId) {
        return Result.success(inboundService.submitApprove(inboundId));
    }

    @PostMapping("/approve/{inboundId}")
    @Operation(summary = "审批通过")
    @Log(module = "入库管理", businessType = BusinessType.APPROVE)
    @SaCheckPermission("inventory:inbound:approve")
    public Result<Boolean> approve(@PathVariable Long inboundId,
                                   @RequestParam Long approverId,
                                   @RequestParam String approverName,
                                   @RequestParam(required = false) String remark) {
        return Result.success(inboundService.approve(inboundId, approverId, approverName, remark));
    }

    @PostMapping("/reject/{inboundId}")
    @Operation(summary = "审批驳回")
    @Log(module = "入库管理", businessType = BusinessType.APPROVE)
    @SaCheckPermission("inventory:inbound:approve")
    public Result<Boolean> reject(@PathVariable Long inboundId,
                                  @RequestParam Long approverId,
                                  @RequestParam String approverName,
                                  @RequestParam String remark) {
        return Result.success(inboundService.reject(inboundId, approverId, approverName, remark));
    }

    @PostMapping("/create-from-purchase/{purchaseOrderId}")
    @Operation(summary = "从采购订单创建入库单")
    @Log(module = "入库管理", businessType = BusinessType.INSERT)
    @SaCheckPermission("inventory:inbound:add")
    public Result<Long> createFromPurchase(@PathVariable Long purchaseOrderId) {
        return Result.success(inboundService.createFromPurchase(purchaseOrderId));
    }

    @PostMapping("/create-from-production/{workOrderId}")
    @Operation(summary = "从生产工单创建入库单")
    @Log(module = "入库管理", businessType = BusinessType.INSERT)
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
    @Log(module = "入库管理", businessType = BusinessType.UPDATE)
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
