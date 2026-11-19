package com.jjx.inventory.migration;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.jjx.common.core.result.Result;
import com.jjx.system.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static com.jjx.inventory.migration.InventoryMigrationData.*;
@RestController
@RequestMapping("/inventory/migration")
@RequiredArgsConstructor
public class InventoryMigrationController {
    private final InventoryMigrationService service;
    @PostMapping("/preview")
    @SaCheckPermission(value={"inventory:migration:import","inventory:material:add"},mode=SaMode.AND)
    public Result<Check> preview(@RequestBody Batch batch) { return Result.success(service.preview(batch)); }
    @PostMapping("/import")
    @SaCheckPermission(value={"inventory:migration:import","inventory:material:add"},mode=SaMode.AND)
    public Result<Imported> importBatch(@RequestBody Batch batch) { return Result.success(service.importBatch(batch,SecurityUtils.getUsername(),SecurityUtils.getDisplayName(),SecurityUtils.getUserId())); }
    @GetMapping("/batches")
    @SaCheckPermission("inventory:stock:view")
    public Result<List<BatchView>> batches() { return Result.success(service.batches()); }
    @GetMapping("/{batchId}/rows")
    @SaCheckPermission("inventory:stock:view")
    public Result<List<RowView>> rows(@PathVariable Long batchId) { return Result.success(service.rows(batchId)); }
    @GetMapping("/{batchId}/rows/{lineId}/cost-history")
    @SaCheckPermission("inventory:stock:view")
    public Result<List<CostAudit>> costHistory(@PathVariable Long batchId,@PathVariable Long lineId) { return Result.success(service.costHistory(batchId,lineId)); }
    @PostMapping("/{batchId}/costs")
    @SaCheckPermission("inventory:migration:cost")
    public Result<CostResult> costs(@PathVariable Long batchId,@RequestBody List<Cost> requests) { return Result.success(service.maintainCosts(batchId,requests,SecurityUtils.getUsername(),SecurityUtils.getDisplayName(),SecurityUtils.getUserId())); }
}
