package com.jjx.inventory.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jjx.common.core.result.Result;
import com.jjx.inventory.dto.query.TransactionQueryDTO;
import com.jjx.inventory.dto.vo.TransactionVO;
import com.jjx.inventory.service.InventoryTransactionService;
import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 库存流水Controller
 */
@RestController
@RequestMapping("/inventory/transaction")
@RequiredArgsConstructor
@Tag(name = "库存流水", description = "库存流水相关接口")
public class InventoryTransactionController {

    private final InventoryTransactionService transactionService;

    @GetMapping("/list")
    @Operation(summary = "分页查询库存流水")
    @SaCheckPermission("inventory:transaction:view")
    public Result<IPage<TransactionVO>> list(TransactionQueryDTO query) {
        return Result.success(transactionService.page(query));
    }

    @GetMapping("/{transactionId}")
    @Operation(summary = "获取流水详情")
    @SaCheckPermission("inventory:transaction:view")
    public Result<TransactionVO> getById(@PathVariable Long transactionId) {
        return Result.success(transactionService.getById(transactionId));
    }

    @GetMapping("/source")
    @Operation(summary = "根据来源单据查询流水")
    @SaCheckPermission("inventory:transaction:view")
    public Result<List<TransactionVO>> getBySource(@RequestParam String sourceType,
                                                   @RequestParam Long sourceId) {
        return Result.success(transactionService.getBySource(sourceType, sourceId));
    }

    @GetMapping("/by-doc-no")
    @Operation(summary = "根据单据号查询流水（DEV-661：出入库详情展示用）")
    @SaCheckPermission("inventory:transaction:view")
    public Result<List<TransactionVO>> getByDocNo(@RequestParam String docNo) {
        return Result.success(transactionService.getByDocNo(docNo));
    }

    @GetMapping("/material/{materialId}")
    @Operation(summary = "查询物料流水记录")
    @SaCheckPermission("inventory:transaction:view")
    public Result<List<TransactionVO>> getByMaterial(@PathVariable Long materialId,
                                                     @RequestParam(defaultValue = "10") int limit) {
        return Result.success(transactionService.getByMaterial(materialId, limit));
    }

    @GetMapping("/time-range")
    @Operation(summary = "查询时间范围内的流水")
    @SaCheckPermission("inventory:transaction:view")
    public Result<List<TransactionVO>> getByTimeRange(@RequestParam String startTime,
                                                      @RequestParam String endTime) {
        return Result.success(transactionService.getByTimeRange(startTime, endTime));
    }

    @GetMapping("/stat/material/{materialId}")
    @Operation(summary = "统计物料出入库数量")
    @SaCheckPermission("inventory:transaction:view")
    public Result<Map<String, Object>> statByMaterial(@PathVariable Long materialId,
                                                      @RequestParam(required = false) String startTime) {
        return Result.success(transactionService.statByMaterial(materialId, startTime));
    }

    @GetMapping("/dashboard")
    @Operation(summary = "获取流水仪表板数据")
    @SaCheckPermission("inventory:transaction:view")
    public Result<Map<String, Object>> getDashboard() {
        // 这里可以添加一些统计信息，比如今日出入库数量、金额等
        return Result.success(Map.of(
                "todayInboundCount", 0,
                "todayOutboundCount", 0,
                "todayInboundAmount", 0,
                "todayOutboundAmount", 0
        ));
    }
}
