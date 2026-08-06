package com.jjx.inventory.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jjx.common.core.result.Result;
import com.jjx.inventory.dto.query.StockCheckDTO;
import com.jjx.inventory.dto.query.StockImportDTO;
import com.jjx.inventory.dto.query.StockQueryDTO;
import com.jjx.inventory.dto.vo.StockCheckVO;
import com.jjx.inventory.dto.vo.StockImportResultVO;
import com.jjx.inventory.dto.vo.StockSummaryVO;
import com.jjx.inventory.dto.vo.StockVO;
import com.jjx.inventory.service.InventoryStockService;
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
 * 库存汇总 Controller
 */
@RestController
@RequestMapping("/inventory/stock")
@RequiredArgsConstructor
@Tag(name = "库存汇总", description = "库存汇总查询接口")
public class InventoryStockController {

    private final InventoryStockService stockService;

    @GetMapping("/list")
    @Operation(summary = "分页查询库存汇总列表")
    @SaCheckPermission("inventory:stock:view")
    public Result<IPage<StockVO>> list(StockQueryDTO query) {
        return Result.success(stockService.page(query));
    }

    @GetMapping("/summary")
    @Operation(summary = "获取库存汇总")
    @SaCheckPermission("inventory:stock:view")
    public Result<StockSummaryVO> summary(StockQueryDTO query) {
        return Result.success(stockService.getSummary(query));
    }

    @GetMapping("/{stockId}")
    @Operation(summary = "获取库存汇总详情")
    @SaCheckPermission("inventory:stock:view")
    public Result<StockVO> getById(@PathVariable Long stockId) {
        return Result.success(stockService.getById(stockId));
    }

    @GetMapping("/material/{materialId}")
    @Operation(summary = "根据物料查询库存汇总")
    @SaCheckPermission("inventory:stock:view")
    public Result<StockVO> getByMaterial(@PathVariable Long materialId) {
        return Result.success(stockService.getByMaterialId(materialId));
    }

    @GetMapping("/warehouse/{warehouseId}")
    @Operation(summary = "根据仓库查询库存汇总")
    @SaCheckPermission("inventory:stock:view")
    public Result<List<StockVO>> getByWarehouse(@PathVariable Long warehouseId) {
        return Result.success(stockService.getByWarehouseId(warehouseId));
    }

    @GetMapping("/alert")
    @Operation(summary = "获取库存预警信息")
    @SaCheckPermission("inventory:stock:view")
    public Result<Map<String, Object>> getAlertInfo() {
        return Result.success(stockService.getAlertInfo());
    }

    @GetMapping("/low-stock")
    @Operation(summary = "查询低库存物料")
    @SaCheckPermission("inventory:stock:view")
    public Result<List<StockVO>> getLowStock() {
        return Result.success(stockService.getLowStock());
    }

    @GetMapping("/expiring")
    @Operation(summary = "查询临期库存")
    @SaCheckPermission("inventory:stock:view")
    public Result<List<StockVO>> getExpiring() {
        return Result.success(stockService.getExpiring());
    }

    @GetMapping("/obsolete")
    @Operation(summary = "查询呆滞库存")
    @SaCheckPermission("inventory:stock:view")
    public Result<List<StockVO>> getObsolete() {
        return Result.success(stockService.getObsolete());
    }

    @GetMapping("/dashboard")
    @Operation(summary = "获取库存仪表板数据")
    @SaCheckPermission("inventory:stock:view")
    public Result<Map<String, Object>> getDashboard() {
        return Result.success(stockService.getDashboardData());
    }

    @PostMapping("/check")
    @Operation(summary = "校验物料并解析仓库库位（用于导入）")
    @SaCheckPermission("inventory:stock:view")
    public Result<StockCheckVO> check(@RequestBody StockCheckDTO checkDTO) {
        return Result.success(stockService.check(checkDTO));
    }

    @PostMapping("/batch-import")
    @Operation(summary = "批量导入库存")
    @Log(module = "库存管理", businessType = BusinessType.IMPORT, bizType = "'stock'", bizId = "#list[0].materialCode")
    @SaCheckPermission("inventory:stock:import")
    public Result<StockImportResultVO> batchImport(@RequestBody List<StockImportDTO> list,
                                                   @RequestParam(required = false, defaultValue = "false") boolean autoCreateLocation) {
        return Result.success(stockService.batchImport(list, autoCreateLocation));
    }

    /**
     * 下载库存导入模板（DEV-672：模板统一由后端生成，不再用静态文件）
     */
    @Operation(summary = "下载库存导入模板")
    @SaCheckPermission("inventory:stock:import")
    @GetMapping("/importTemplate")
    public void importTemplate(jakarta.servlet.http.HttpServletResponse response) {
        com.jjx.common.utils.ExcelUtils.downloadTemplate(response,
                com.jjx.inventory.dto.imports.StockImportTemplateDTO.class, "库存导入模板");
    }
}
