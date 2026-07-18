package com.jjx.inventory.controller;

import com.jjx.common.core.result.Result;
import com.jjx.inventory.service.InventoryReportService;
import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 库存报表统计Controller
 */
@RestController
@RequestMapping("/inventory/report")
@RequiredArgsConstructor
@Tag(name = "库存报表", description = "库存报表统计相关接口")
public class InventoryReportController {

    private final InventoryReportService reportService;

    @GetMapping("/turnover")
    @Operation(summary = "库存周转率统计")
    @SaCheckPermission("inventory:report:view")
    public Result<Map<String, Object>> calculateTurnover(@RequestParam String startDate,
                                                         @RequestParam String endDate) {
        return Result.success(reportService.calculateTurnover(startDate, endDate));
    }

    @GetMapping("/cost")
    @Operation(summary = "库存成本统计")
    @SaCheckPermission("inventory:report:view")
    public Result<Map<String, Object>> calculateCost(@RequestParam String date) {
        return Result.success(reportService.calculateCost(date));
    }

    @GetMapping("/in-out-stat")
    @Operation(summary = "出入库统计")
    @SaCheckPermission("inventory:report:view")
    public Result<Map<String, Object>> statInOut(@RequestParam String startDate,
                                                 @RequestParam String endDate) {
        return Result.success(reportService.statInOut(startDate, endDate));
    }

    @GetMapping("/abc-analysis")
    @Operation(summary = "ABC分析")
    @SaCheckPermission("inventory:report:view")
    public Result<List<Map<String, Object>>> abcAnalysis() {
        return Result.success(reportService.abcAnalysis());
    }

    @GetMapping("/warehouse-stock-stat")
    @Operation(summary = "仓库库存统计")
    @SaCheckPermission("inventory:report:view")
    public Result<List<Map<String, Object>>> warehouseStockStat() {
        return Result.success(reportService.warehouseStockStat());
    }

    @GetMapping("/material-trend")
    @Operation(summary = "物料库存趋势")
    @SaCheckPermission("inventory:report:view")
    public Result<List<Map<String, Object>>> materialTrend(@RequestParam Long materialId,
                                                           @RequestParam(defaultValue = "30") int days) {
        return Result.success(reportService.materialTrend(materialId, days));
    }

    @GetMapping("/alert-stat")
    @Operation(summary = "库存预警统计")
    @SaCheckPermission("inventory:report:view")
    public Result<Map<String, Object>> alertStat() {
        return Result.success(reportService.alertStat());
    }

    @GetMapping("/stocktake-diff-stat")
    @Operation(summary = "盘点差异统计")
    @SaCheckPermission("inventory:report:view")
    public Result<Map<String, Object>> stocktakeDiffStat(@RequestParam String startDate,
                                                         @RequestParam String endDate) {
        return Result.success(reportService.stocktakeDiffStat(startDate, endDate));
    }

    @GetMapping("/transfer-stat")
    @Operation(summary = "调拨统计")
    @SaCheckPermission("inventory:report:view")
    public Result<Map<String, Object>> transferStat(@RequestParam String startDate,
                                                    @RequestParam String endDate) {
        return Result.success(reportService.transferStat(startDate, endDate));
    }

    @GetMapping("/category-stock-stat")
    @Operation(summary = "物料分类库存统计")
    @SaCheckPermission("inventory:report:view")
    public Result<List<Map<String, Object>>> categoryStockStat() {
        return Result.success(reportService.categoryStockStat());
    }

    @GetMapping("/obsolete-analysis")
    @Operation(summary = "呆滞料分析")
    @SaCheckPermission("inventory:report:view")
    public Result<List<Map<String, Object>>> obsoleteAnalysis(@RequestParam(defaultValue = "90") int days) {
        return Result.success(reportService.obsoleteAnalysis(days));
    }

    @GetMapping("/expiry-analysis")
    @Operation(summary = "保质期分析")
    @SaCheckPermission("inventory:report:view")
    public Result<List<Map<String, Object>>> expiryAnalysis(@RequestParam(defaultValue = "30") int days) {
        return Result.success(reportService.expiryAnalysis(days));
    }

    @GetMapping("/dashboard")
    @Operation(summary = "获取报表仪表板数据")
    @SaCheckPermission("inventory:report:view")
    public Result<Map<String, Object>> getDashboard() {
        // 这里可以添加一些统计信息，比如总库存价值、周转率等
        return Result.success(Map.of(
                "totalStockValue", 0,
                "turnoverRate", 0,
                "inboundCount", 0,
                "outboundCount", 0
        ));
    }
}
