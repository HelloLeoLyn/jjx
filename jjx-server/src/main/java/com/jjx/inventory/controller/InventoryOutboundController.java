package com.jjx.inventory.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jjx.common.core.result.Result;
import com.jjx.inventory.dto.query.OutboundQueryDTO;
import com.jjx.inventory.dto.vo.OutboundVO;
import com.jjx.inventory.service.InventoryOutboundService;
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
 * 出库管理Controller
 */
@RestController
@RequestMapping("/inventory/outbound")
@RequiredArgsConstructor
@Tag(name = "出库管理", description = "出库管理相关接口")
public class InventoryOutboundController {

    private final InventoryOutboundService outboundService;

    @GetMapping("/list")
    @Operation(summary = "分页查询出库单")
    @SaCheckPermission("inventory:outbound:view")
    public Result<IPage<OutboundVO>> list(OutboundQueryDTO query) {
        return Result.success(outboundService.page(query));
    }

    @GetMapping("/{outboundId}")
    @Operation(summary = "获取出库单详情")
    @SaCheckPermission("inventory:outbound:view")
    public Result<OutboundVO> getById(@PathVariable Long outboundId) {
        return Result.success(outboundService.getDetail(outboundId));
    }

    @PostMapping("/create")
    @Operation(summary = "创建出库单")
    @Log(module = "出库管理", businessType = BusinessType.INSERT, bizType = "'outbound'", bizId = "#result.data")
    @SaCheckPermission("inventory:outbound:add")
    public Result<Long> create(@RequestBody Map<String, Object> params) {
        return Result.success(outboundService.create(params));
    }

    @PostMapping("/confirm/{outboundId}")
    @Operation(summary = "确认出库")
    @Log(module = "出库管理", businessType = BusinessType.UPDATE, bizType = "'outbound'", bizId = "#outboundId", bizStatus = "10")
    @SaCheckPermission("inventory:outbound:edit")
    public Result<Boolean> confirm(@PathVariable Long outboundId,
                                   @RequestParam Long operatorId,
                                   @RequestParam String operatorName) {
        return Result.success(outboundService.confirm(outboundId, operatorId, operatorName));
    }

    @PutMapping("/update")
    @Operation(summary = "更新出库单（含明细）")
    @Log(module = "出库管理", businessType = BusinessType.UPDATE, bizType = "'outbound'", bizId = "#params.outboundId")
    @SaCheckPermission("inventory:outbound:edit")
    public Result<Boolean> update(@RequestBody Map<String, Object> params) {
        return Result.success(outboundService.update(params));
    }

    @PostMapping("/cancel/{outboundId}")
    @Operation(summary = "取消出库单")
    @Log(module = "出库管理", businessType = BusinessType.UPDATE, bizType = "'outbound'", bizId = "#outboundId", bizStatus = "9")
    @SaCheckPermission("inventory:outbound:edit")
    public Result<Boolean> cancel(@PathVariable Long outboundId,
                                  @RequestParam String reason) {
        return Result.success(outboundService.cancel(outboundId, reason));
    }

    @PostMapping("/submit-approve/{outboundId}")
    @Operation(summary = "提交审批")
    @Log(module = "出库管理", businessType = BusinessType.UPDATE, bizType = "'outbound'", bizId = "#outboundId", bizStatus = "1")
    @SaCheckPermission("inventory:outbound:edit")
    public Result<Boolean> submitApprove(@PathVariable Long outboundId) {
        return Result.success(outboundService.submitApprove(outboundId));
    }

    @PostMapping("/approve/{outboundId}")
    @Operation(summary = "审批通过")
    @Log(module = "出库管理", businessType = BusinessType.APPROVE, bizType = "'outbound'", bizId = "#outboundId", bizStatus = "2")
    @SaCheckPermission("inventory:outbound:approve")
    public Result<Boolean> approve(@PathVariable Long outboundId,
                                   @RequestParam Long approverId,
                                   @RequestParam String approverName,
                                   @RequestParam(required = false) String remark) {
        return Result.success(outboundService.approve(outboundId, approverId, approverName, remark));
    }

    @PostMapping("/reject/{outboundId}")
    @Operation(summary = "审批驳回")
    @Log(module = "出库管理", businessType = BusinessType.APPROVE, bizType = "'outbound'", bizId = "#outboundId", bizStatus = "3")
    @SaCheckPermission("inventory:outbound:approve")
    public Result<Boolean> reject(@PathVariable Long outboundId,
                                  @RequestParam Long approverId,
                                  @RequestParam String approverName,
                                  @RequestParam String remark) {
        return Result.success(outboundService.reject(outboundId, approverId, approverName, remark));
    }

    @PostMapping("/create-from-production/{workOrderId}")
    @Operation(summary = "从生产工单创建出库单")
    @Log(module = "出库管理", businessType = BusinessType.INSERT, bizType = "'outbound'", bizId = "#workOrderId")
    @SaCheckPermission("inventory:outbound:add")
    public Result<Long> createFromProduction(@PathVariable Long workOrderId) {
        return Result.success(outboundService.createFromProduction(workOrderId));
    }

    @PostMapping("/create-from-sales/{salesOrderId}")
    @Operation(summary = "从销售订单创建出库单")
    @Log(module = "出库管理", businessType = BusinessType.INSERT, bizType = "'outbound'", bizId = "#salesOrderId")
    @SaCheckPermission("inventory:outbound:add")
    public Result<Long> createFromSales(@PathVariable Long salesOrderId) {
        return Result.success(outboundService.createFromSales(salesOrderId));
    }

    @GetMapping("/pending-approval")
    @Operation(summary = "查询待审批的出库单")
    @SaCheckPermission("inventory:outbound:view")
    public Result<List<OutboundVO>> getPendingApproval() {
        return Result.success(outboundService.getPendingApproval());
    }

    @GetMapping("/date-range")
    @Operation(summary = "查询日期范围内的出库单")
    @SaCheckPermission("inventory:outbound:view")
    public Result<List<OutboundVO>> getByDateRange(@RequestParam String startDate,
                                                   @RequestParam String endDate) {
        return Result.success(outboundService.getByDateRange(startDate, endDate));
    }

    @GetMapping("/source")
    @Operation(summary = "根据来源单据查询出库单")
    @SaCheckPermission("inventory:outbound:view")
    public Result<OutboundVO> getBySource(@RequestParam String sourceType,
                                          @RequestParam Long sourceId) {
        return Result.success(outboundService.getBySource(sourceType, sourceId));
    }

    @PostMapping("/update-status/{outboundId}")
    @Operation(summary = "更新出库单状态")
    @Log(module = "出库管理", businessType = BusinessType.UPDATE, bizType = "'outbound'", bizId = "#outboundId")
    @SaCheckPermission("inventory:outbound:edit")
    public Result<Boolean> updateStatus(@PathVariable Long outboundId,
                                        @RequestParam Integer status) {
        return Result.success(outboundService.updateStatus(outboundId, status));
    }

    @GetMapping("/export-pdf/{outboundId}")
    @Operation(summary = "导出出库单PDF（单张表单）")
    @Log(module = "出库管理", businessType = BusinessType.EXPORT, bizType = "'outbound'", bizId = "#outboundId")
    @SaCheckPermission("inventory:outbound:view")
    public void exportPdf(@PathVariable Long outboundId, jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        byte[] bytes = outboundService.exportPdf(outboundId);
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=outbound_" + outboundId + ".pdf");
        response.getOutputStream().write(bytes);
    }

    @GetMapping("/dashboard")
    @Operation(summary = "获取出库仪表板数据")
    @SaCheckPermission("inventory:outbound:view")
    public Result<Map<String, Object>> getDashboard() {
        // 这里可以添加一些统计信息，比如今日出库数量、金额等
        return Result.success(Map.of(
                "todayOutboundCount", 0,
                "todayOutboundAmount", 0,
                "pendingApprovalCount", 0,
                "pendingConfirmCount", 0
        ));
    }
}
