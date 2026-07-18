package com.jjx.inventory.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jjx.common.core.result.Result;
import com.jjx.inventory.dto.query.StockItemQueryDTO;
import com.jjx.inventory.dto.vo.StockItemVO;
import com.jjx.inventory.service.InventoryStockItemService;
import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 库存批次明细 Controller
 */
@RestController
@RequestMapping("/inventory/stock-item")
@RequiredArgsConstructor
@Tag(name = "库存批次明细", description = "库存批次明细查询接口")
public class InventoryStockItemController {

    private final InventoryStockItemService stockItemService;

    @GetMapping("/list")
    @Operation(summary = "分页查询库存批次明细")
    @SaCheckPermission("inventory:stock:view")
    public Result<IPage<StockItemVO>> list(StockItemQueryDTO query) {
        return Result.success(stockItemService.page(query));
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
