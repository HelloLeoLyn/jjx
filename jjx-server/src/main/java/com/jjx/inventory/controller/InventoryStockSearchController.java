package com.jjx.inventory.controller;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.common.core.result.Result;
import com.jjx.inventory.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/** 只提供库存查询所需选项，不要求仓库/库位管理权限。 */
@RestController
@RequestMapping("/inventory/stock/search")
@RequiredArgsConstructor
public class InventoryStockSearchController {
    private final InventoryWarehouseService warehouses;
    private final InventoryStorageLocationService locations;
    public record Option(Long value,String label) { }
    @GetMapping("/warehouses")
    @SaCheckPermission("inventory:stock:view")
    public Result<List<Option>> warehouses() {
        return Result.success(warehouses.getOptions().stream().map(w->new Option(w.getWarehouseId(),w.getWarehouseName())).toList());
    }
    @GetMapping("/locations")
    @SaCheckPermission("inventory:stock:view")
    public Result<List<Option>> locations(@RequestParam Long warehouseId) {
        var rows=locations.getByWarehouseId(warehouseId);
        var parentIds=rows.stream().map(com.jjx.inventory.domain.InventoryStorageLocation::getParentId).filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        return Result.success(rows.stream().filter(l->!parentIds.contains(l.getLocationId()))
            .map(l->new Option(l.getLocationId(),l.getLocationCode()+" · "+l.getLocationName())).toList());
    }
}
