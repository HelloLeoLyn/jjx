package com.jjx.production.controller;
import com.jjx.common.core.page.PageResult;
import com.jjx.common.core.result.Result;
import com.jjx.production.domain.dto.EquipmentQueryDTO;
import com.jjx.production.domain.entity.ProductionEquipment;
import com.jjx.production.service.EquipmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@Tag(name = "设备管理")
@RestController
@RequestMapping("/production/equipment")
@RequiredArgsConstructor
public class EquipmentController {
    private final EquipmentService equipmentService;

    @Operation(summary = "分页查询")
    @GetMapping("/page")
    public Result<PageResult<ProductionEquipment>> page(EquipmentQueryDTO query) {
        return Result.success(equipmentService.page(query));
    }

    @Operation(summary = "查询列表")
    @GetMapping("/list")
    public Result<List<ProductionEquipment>> list(EquipmentQueryDTO query) {
        return Result.success(equipmentService.list(query));
    }

    @Operation(summary = "查询详情")
    @GetMapping("/{id}")
    public Result<ProductionEquipment> getById(@PathVariable Long id) {
        return Result.success(equipmentService.getById(id));
    }

    @Operation(summary = "新增设备")
    @PostMapping
    public Result<Long> create(@RequestBody ProductionEquipment entity) {
        return Result.success(equipmentService.create(entity));
    }

    @Operation(summary = "修改设备")
    @PutMapping
    public Result<Void> update(@RequestBody ProductionEquipment entity) {
        equipmentService.update(entity);
        return Result.success();
    }

    @Operation(summary = "删除设备")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        equipmentService.delete(id);
        return Result.success();
    }
}
