package com.jjx.inventory.stocktake;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jjx.common.core.result.Result;
import com.jjx.framework.common.controller.BaseController;
import com.jjx.system.annotation.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import static com.jjx.inventory.stocktake.QuickStocktakeData.*;
@RestController
@RequestMapping("/inventory/stocktake/quick")
@RequiredArgsConstructor
public class QuickStocktakeController extends BaseController {
    private final QuickStocktakeService service;
    @GetMapping("/batches")
    @SaCheckPermission(value={"inventory:stock:view","inventory:stocktake:view"},mode=SaMode.AND)
    public Result<IPage<Batch>> batches(@RequestParam Long inventoryItemId,@RequestParam(required=false) Long warehouseId,
        @RequestParam(required=false) Long locationId,@RequestParam(defaultValue="1") int pageNum,@RequestParam(defaultValue="10") int pageSize) {
        return Result.success(service.batches(inventoryItemId,warehouseId,locationId,pageNum,pageSize));
    }
    @GetMapping("/records")
    @SaCheckPermission("inventory:stocktake:view")
    public Result<IPage<Receipt>> records(@RequestParam Long inventoryItemId,@RequestParam(defaultValue="1") int pageNum,@RequestParam(defaultValue="10") int pageSize) {
        return Result.success(service.records(inventoryItemId,pageNum,pageSize));
    }
    @PostMapping("/preview")
    @SaCheckPermission("inventory:stocktake:add")
    public Result<Preview> preview(@RequestBody Save request) {return Result.success(service.preview(request));}
    @PostMapping
    @SaCheckPermission("inventory:stocktake:add")
    @Log(module="盘点管理",businessType=BusinessType.INSERT,action="快捷盘点保存")
    public Result<Receipt> save(@RequestBody Save request) {return Result.success(service.save(request,getUserId(),com.jjx.system.utils.SecurityUtils.getDisplayName(),com.jjx.system.utils.SecurityUtils.getUsername()));}
    @PostMapping("/{id}/cancel")
    @SaCheckPermission("inventory:stocktake:edit")
    @Log(module="盘点管理",businessType=BusinessType.UPDATE,action="快捷盘点作废")
    public Result<Receipt> cancel(@PathVariable Long id) {return Result.success(service.cancel(id,com.jjx.system.utils.SecurityUtils.getUsername()));}
    @PostMapping("/{id}/confirm")
    @SaCheckPermission("inventory:stocktake:approve")
    @Log(module="盘点管理",businessType=BusinessType.UPDATE,action="快捷盘点确认过账")
    public Result<Receipt> confirm(@PathVariable Long id) {return Result.success(service.confirm(id,getUserId(),com.jjx.system.utils.SecurityUtils.getDisplayName(),com.jjx.system.utils.SecurityUtils.getUsername()));}
}
