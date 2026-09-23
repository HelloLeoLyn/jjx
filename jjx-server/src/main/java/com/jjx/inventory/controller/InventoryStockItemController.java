package com.jjx.inventory.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jjx.common.core.result.Result;
import com.jjx.inventory.dto.query.StockItemQueryDTO;
import com.jjx.inventory.dto.vo.StockItemVO;
import com.jjx.inventory.service.InventoryStockItemService;
import com.jjx.inventory.service.InventoryTransactionService;
import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 库存批次明细 Controller
 */
@RestController
@RequestMapping("/inventory/stock-item")
@RequiredArgsConstructor
@Tag(name = "库存批次明细", description = "库存批次明细查询接口")
public class InventoryStockItemController {

    private final InventoryStockItemService stockItemService;
    private final InventoryTransactionService transactionService;

    @GetMapping("/list")
    @Operation(summary = "分页查询库存批次明细")
    @SaCheckPermission("inventory:stock:view")
    public Result<IPage<StockItemVO>> list(StockItemQueryDTO query) {
        return Result.success(stockItemService.page(query));
    }

    /**
     * 批次收发存汇总（dev-20260923-017）：同一库存物品下每个批次的 入库合计 / 出库合计 / 结存。
     * 数据源 = 库存流水（唯一真源），不依赖批次表新增字段；权限同“库存台账”（inventory:stock:view），
     * 使台账页总能显示三栏；明细流水（单据号/操作人）另走 /inventory/transaction/by-batch（需 inventory:transaction:view）。
     */
    @GetMapping("/batch-summary")
    @Operation(summary = "批次收发存汇总（入库/出库/结存，按库存物品）")
    @SaCheckPermission("inventory:stock:view")
    public Result<List<Map<String, Object>>> batchSummary(@RequestParam Long inventoryItemId) {
        return Result.success(transactionService.batchFlowSummary(inventoryItemId));
    }

    @GetMapping("/{itemId}")
    @Operation(summary = "获取库存批次明细详情")
    @SaCheckPermission("inventory:stock:view")
    public Result<StockItemVO> getById(@PathVariable Long itemId) {
        return Result.success(stockItemService.getById(itemId));
    }

    @GetMapping("/material/{materialId}")
    @Operation(summary = "根据物料ID查询批次明细")
    @SaCheckPermission("inventory:stock:view")
    public Result<List<StockItemVO>> getByMaterial(@PathVariable Long materialId) {
        return Result.success(stockItemService.getByMaterialId(materialId));
    }

    @GetMapping("/material/{materialId}/warehouse/{warehouseId}")
    @Operation(summary = "根据物料ID和仓库ID查询批次明细")
    @SaCheckPermission("inventory:stock:view")
    public Result<List<StockItemVO>> getByMaterialAndWarehouse(
            @PathVariable Long materialId,
            @PathVariable Long warehouseId) {
        return Result.success(stockItemService.getByMaterialAndWarehouse(materialId, warehouseId));
    }
}
