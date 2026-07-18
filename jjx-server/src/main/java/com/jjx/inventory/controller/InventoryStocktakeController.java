package com.jjx.inventory.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jjx.common.core.result.Result;
import com.jjx.inventory.dto.query.StocktakeQueryDTO;
import com.jjx.inventory.dto.vo.StocktakeVO;
import com.jjx.inventory.service.InventoryStocktakeService;
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
 * 盘点管理Controller
 */
@RestController
@RequestMapping("/inventory/stocktake")
@RequiredArgsConstructor
@Tag(name = "盘点管理", description = "盘点管理相关接口")
public class InventoryStocktakeController {

    private final InventoryStocktakeService stocktakeService;

    @GetMapping("/list")
    @Operation(summary = "分页查询盘点单")
    @SaCheckPermission("inventory:stocktake:view")
    public Result<IPage<StocktakeVO>> list(StocktakeQueryDTO query) {
        return Result.success(stocktakeService.page(query));
    }

    @GetMapping("/{stocktakeId}")
    @Operation(summary = "获取盘点单详情")
    @SaCheckPermission("inventory:stocktake:view")
    public Result<StocktakeVO> getById(@PathVariable Long stocktakeId) {
        return Result.success(stocktakeService.getDetail(stocktakeId));
    }

    @PostMapping("/create")
    @Operation(summary = "创建盘点单")
    @Log(module = "盘点管理", businessType = BusinessType.INSERT)
    @SaCheckPermission("inventory:stocktake:add")
    public Result<Long> create(@RequestBody Map<String, Object> params) {
        return Result.success(stocktakeService.create(params));
    }

    @PostMapping("/start/{stocktakeId}")
    @Operation(summary = "开始盘点")
    @Log(module = "盘点管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("inventory:stocktake:edit")
    public Result<Boolean> startStocktake(@PathVariable Long stocktakeId) {
        return Result.success(stocktakeService.startStocktake(stocktakeId));
    }

    @PostMapping("/input-data/{stocktakeId}")
    @Operation(summary = "录入盘点数据")
    @Log(module = "盘点管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("inventory:stocktake:edit")
    public Result<Boolean> inputStocktakeData(@PathVariable Long stocktakeId,
                                              @RequestBody List<Map<String, Object>> items) {
        return Result.success(stocktakeService.inputStocktakeData(stocktakeId, items));
    }

    @GetMapping("/calculate-diff/{stocktakeId}")
    @Operation(summary = "计算盘点差异")
    @SaCheckPermission("inventory:stocktake:view")
    public Result<Map<String, Object>> calculateDiff(@PathVariable Long stocktakeId) {
        return Result.success(stocktakeService.calculateDiff(stocktakeId));
    }

    @PostMapping("/confirm-result/{stocktakeId}")
    @Operation(summary = "确认盘点结果")
    @Log(module = "盘点管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("inventory:stocktake:edit")
    public Result<Boolean> confirmResult(@PathVariable Long stocktakeId) {
        return Result.success(stocktakeService.confirmResult(stocktakeId));
    }

    @PostMapping("/process-diff/{stocktakeId}")
    @Operation(summary = "处理盈亏")
    @Log(module = "盘点管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("inventory:stocktake:edit")
    public Result<Boolean> processDiff(@PathVariable Long stocktakeId,
                                       @RequestParam Long operatorId,
                                       @RequestParam String operatorName) {
        return Result.success(stocktakeService.processDiff(stocktakeId, operatorId, operatorName));
    }

    @PostMapping("/close/{stocktakeId}")
    @Operation(summary = "关闭盘点单")
    @Log(module = "盘点管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("inventory:stocktake:edit")
    public Result<Boolean> closeStocktake(@PathVariable Long stocktakeId) {
        return Result.success(stocktakeService.closeStocktake(stocktakeId));
    }

    @PostMapping("/submit-approve/{stocktakeId}")
    @Operation(summary = "提交审批")
    @Log(module = "盘点管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("inventory:stocktake:edit")
    public Result<Boolean> submitApprove(@PathVariable Long stocktakeId) {
        return Result.success(stocktakeService.submitApprove(stocktakeId));
    }

    @PostMapping("/approve/{stocktakeId}")
    @Operation(summary = "审批通过")
    @Log(module = "盘点管理", businessType = BusinessType.APPROVE)
    @SaCheckPermission("inventory:stocktake:approve")
    public Result<Boolean> approve(@PathVariable Long stocktakeId,
                                   @RequestParam Long approverId,
                                   @RequestParam String approverName,
                                   @RequestParam(required = false) String remark) {
        return Result.success(stocktakeService.approve(stocktakeId, approverId, approverName, remark));
    }

    @GetMapping("/processing")
    @Operation(summary = "查询进行中的盘点单")
    @SaCheckPermission("inventory:stocktake:view")
    public Result<List<StocktakeVO>> getProcessing() {
        return Result.success(stocktakeService.getProcessing());
    }

    @GetMapping("/pending-approval")
    @Operation(summary = "查询待审批的盘点单")
    @SaCheckPermission("inventory:stocktake:view")
    public Result<List<StocktakeVO>> getPendingApproval() {
        return Result.success(stocktakeService.getPendingApproval());
    }

    @PostMapping("/update-status/{stocktakeId}")
    @Operation(summary = "更新盘点单状态")
    @Log(module = "盘点管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("inventory:stocktake:edit")
    public Result<Boolean> updateStatus(@PathVariable Long stocktakeId,
                                        @RequestParam String status) {
        return Result.success(stocktakeService.updateStatus(stocktakeId, status));
    }

    @GetMapping("/dashboard")
    @Operation(summary = "获取盘点仪表板数据")
    @SaCheckPermission("inventory:stocktake:view")
    public Result<Map<String, Object>> getDashboard() {
        // 这里可以添加一些统计信息，比如进行中的盘点数量、待审批的盘点数量等
        return Result.success(Map.of(
                "processingCount", 0,
                "pendingApprovalCount", 0,
                "completedThisMonth", 0,
                "totalDiffAmount", 0
        ));
    }
}
