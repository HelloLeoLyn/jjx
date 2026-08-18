package com.jjx.inventory.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jjx.common.core.result.Result;
import com.jjx.inventory.dto.query.AlertQueryDTO;
import com.jjx.inventory.dto.vo.AlertVO;
import com.jjx.inventory.service.InventoryAlertService;
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
 * 库存预警管理Controller
 */
@RestController
@RequestMapping("/inventory/alert")
@RequiredArgsConstructor
@Tag(name = "库存预警", description = "库存预警相关接口")
public class InventoryAlertController {

    private final InventoryAlertService alertService;

    @GetMapping("/list")
    @Operation(summary = "分页查询预警列表")
    @SaCheckPermission("inventory:alert:view")
    public Result<IPage<AlertVO>> list(AlertQueryDTO query) {
        return Result.success(alertService.page(query));
    }

    @PostMapping("/execute-check")
    @Operation(summary = "执行预警检查")
    @Log(module = "库存预警", businessType = BusinessType.UPDATE, bizType = "'alert'", bizId = "'batch'")
    @SaCheckPermission("inventory:alert:edit")
    public Result<Void> executeAlertCheck() {
        alertService.executeAlertCheck();
        return Result.success();
    }

    @PostMapping("/check-safe-stock")
    @Operation(summary = "检查安全库存预警")
    @Log(module = "库存预警", businessType = BusinessType.UPDATE, bizType = "'alert'", bizId = "'batch'")
    @SaCheckPermission("inventory:alert:edit")
    public Result<Void> checkSafeStockAlert() {
        alertService.checkSafeStockAlert();
        return Result.success();
    }

    @PostMapping("/check-max-stock")
    @Operation(summary = "检查最高库存预警")
    @Log(module = "库存预警", businessType = BusinessType.UPDATE, bizType = "'alert'", bizId = "'batch'")
    @SaCheckPermission("inventory:alert:edit")
    public Result<Void> checkMaxStockAlert() {
        alertService.checkMaxStockAlert();
        return Result.success();
    }

    @PostMapping("/check-expiry")
    @Operation(summary = "检查保质期预警")
    @Log(module = "库存预警", businessType = BusinessType.UPDATE, bizType = "'alert'", bizId = "'batch'")
    @SaCheckPermission("inventory:alert:edit")
    public Result<Void> checkExpiryAlert() {
        alertService.checkExpiryAlert();
        return Result.success();
    }

    @PostMapping("/check-obsolete")
    @Operation(summary = "检查呆滞料预警")
    @Log(module = "库存预警", businessType = BusinessType.UPDATE, bizType = "'alert'", bizId = "'batch'")
    @SaCheckPermission("inventory:alert:edit")
    public Result<Void> checkObsoleteAlert() {
        alertService.checkObsoleteAlert();
        return Result.success();
    }

    @PostMapping("/check-order-shortage/{orderId}")
    @Operation(summary = "订单齐套检查（按BOM算料缺料预警，返回缺料明细含在途/实际缺口）")
    @Log(module = "库存预警", businessType = BusinessType.UPDATE, bizType = "'alert'", bizId = "#orderId")
    @SaCheckPermission("inventory:alert:edit")
    public Result<java.util.List<java.util.Map<String, Object>>> checkOrderShortage(@PathVariable Long orderId) {
        return Result.success(alertService.checkOrderShortageWithDetail(orderId));
    }

    @PostMapping("/check-global-shortage")
    @Operation(summary = "全局汇总缺料检查（082：在途订单BOM汇总→物料缺口预警，手动触发）")
    @Log(module = "库存预警", businessType = BusinessType.UPDATE, bizType = "'alert'")
    @SaCheckPermission("inventory:alert:edit")
    public Result<Void> checkGlobalShortage() {
        alertService.checkGlobalShortage();
        return Result.success();
    }

    @GetMapping("/count-unprocessed-shortage/{orderId}")
    @Operation(summary = "查询订单未处理缺料预警数（DEV-583 前端确认后弹窗提示）")
    public Result<Long> countUnprocessedShortage(@PathVariable Long orderId) {
        return Result.success(alertService.countUnprocessedOrderShortage(orderId));
    }

    @PostMapping("/mark-read/{alertId}")
    @Operation(summary = "标记预警已读")
    @Log(module = "库存预警", businessType = BusinessType.UPDATE, bizType = "'alert'", bizId = "#alertId")
    @SaCheckPermission("inventory:alert:edit")
    public Result<Boolean> markRead(@PathVariable Long alertId) {
        return Result.success(alertService.markRead(alertId));
    }

    @PostMapping("/batch-mark-read")
    @Operation(summary = "批量标记已读")
    @Log(module = "库存预警", businessType = BusinessType.UPDATE, bizType = "'alert'", bizId = "#alertIds[0]")
    @SaCheckPermission("inventory:alert:edit")
    public Result<Boolean> batchMarkRead(@RequestBody List<Long> alertIds) {
        return Result.success(alertService.batchMarkRead(alertIds));
    }

    @PostMapping("/process/{alertId}")
    @Operation(summary = "处理预警")
    @Log(module = "库存预警", businessType = BusinessType.UPDATE, bizType = "'alert'", bizId = "#alertId")
    @SaCheckPermission("inventory:alert:edit")
    public Result<Boolean> processAlert(@PathVariable Long alertId,
                                        @RequestParam String processedBy,
                                        @RequestParam(required = false) String remark) {
        return Result.success(alertService.processAlert(alertId, processedBy, remark));
    }

    @PostMapping("/batch-process")
    @Operation(summary = "批量处理预警（采购计划确认后回写，关联采购订单号）")
    @Log(module = "库存预警", businessType = BusinessType.UPDATE, bizType = "'alert'", bizId = "#dto.alertIds[0]")
    // 2026-08-18：调用方为采购计划工作台（采购角色），权限从 inventory:alert:edit 改为 purchase:order:add
    @SaCheckPermission("purchase:order:add")
    public Result<Boolean> batchProcessAlert(@RequestBody com.jjx.inventory.domain.dto.AlertBatchProcessDTO dto) {
        return Result.success(alertService.batchProcessAlert(dto.getAlertIds(), dto.getMaterialIds(), dto.getRelatedOrderNo(), dto.getRemark()));
    }

    @GetMapping("/purchase-suggestions")
    @Operation(summary = "生成采购建议")
    @SaCheckPermission("inventory:alert:view")
    public Result<List<Map<String, Object>>> generatePurchaseSuggestions() {
        return Result.success(alertService.generatePurchaseSuggestions());
    }

    @GetMapping("/unprocessed")
    @Operation(summary = "查询未处理的预警")
    @SaCheckPermission("inventory:alert:view")
    public Result<List<AlertVO>> getUnprocessed() {
        return Result.success(alertService.getUnprocessed());
    }

    @GetMapping("/exists-unprocessed")
    @Operation(summary = "查询指定物料是否存在未处理的预警")
    @SaCheckPermission("inventory:alert:view")
    public Result<Boolean> existsUnprocessed(@RequestParam String alertType,
                                             @RequestParam Long materialId) {
        return Result.success(alertService.existsUnprocessed(alertType, materialId));
    }

    @GetMapping("/dashboard")
    @Operation(summary = "获取预警仪表板数据")
    @SaCheckPermission("inventory:alert:view")
    public Result<Map<String, Object>> getDashboard() {
        // 这里可以添加一些统计信息，比如未处理的预警数量、紧急预警数量等
        return Result.success(Map.of(
                "unprocessedCount", 0,
                "urgentCount", 0,
                "warningCount", 0,
                "infoCount", 0
        ));
    }
}
