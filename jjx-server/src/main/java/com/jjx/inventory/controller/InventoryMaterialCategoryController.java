package com.jjx.inventory.controller;

import com.jjx.common.constant.LogActions;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.common.core.result.Result;
import com.jjx.inventory.domain.InventoryMaterialCategory;
import com.jjx.inventory.dto.query.CategoryQueryDTO;
import com.jjx.inventory.dto.save.CategorySaveDTO;
import com.jjx.inventory.dto.update.CategoryUpdateDTO;
import com.jjx.inventory.dto.vo.CategoryTreeVO;
import com.jjx.inventory.dto.vo.MaterialCategoryVO;
import com.jjx.inventory.service.InventoryMaterialCategoryService;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.annotation.Log;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 物料分类管理Controller
 */
@RestController
@RequestMapping("/inventory/material-category")
@RequiredArgsConstructor
public class InventoryMaterialCategoryController {

    private final InventoryMaterialCategoryService categoryService;

    @GetMapping("/tree")
    @SaCheckPermission("inventory:category:list")
    public Result<List<MaterialCategoryVO>> getTree(CategoryQueryDTO queryDTO) {
        return Result.success(categoryService.getCategoryTree(queryDTO));
    }

    @GetMapping("/list")
    @SaCheckPermission("inventory:category:list")
    public Result<List<MaterialCategoryVO>> list(CategoryQueryDTO queryDTO) {
        return Result.success(categoryService.getCategoryTree(queryDTO));
    }

    @GetMapping("/{categoryId}")
//    @Operation(summary = "获取分类详情")
    @SaCheckPermission("inventory:category:query")
    public Result<CategoryTreeVO> getById(@PathVariable Long categoryId) {
        return Result.success(categoryService.getTreeById(categoryId));
    }

    @PostMapping
//    @Operation(summary = "新增分类")
    @Log(module = "物料分类管理", businessType = BusinessType.INSERT, bizType = "'material_category'", bizId = "#dto.categoryId", action = LogActions.MAT_CATEGORY_CREATE)
    @SaCheckPermission("inventory:category:add")
    public Result<Boolean> add(@RequestBody CategorySaveDTO dto) {
        InventoryMaterialCategory entity = new InventoryMaterialCategory();
        BeanUtils.copyProperties(dto, entity);
        return Result.success(categoryService.save(entity));
    }

    @PutMapping
//    @Operation(summary = "修改分类")
    @Log(module = "物料分类管理", businessType = BusinessType.UPDATE, bizType = "'material_category'", bizId = "#dto.categoryId", action = LogActions.MAT_CATEGORY_EDIT)
    @SaCheckPermission("inventory:category:edit")
    public Result<Boolean> update(@RequestBody CategoryUpdateDTO dto) {
        InventoryMaterialCategory entity = new InventoryMaterialCategory();
        BeanUtils.copyProperties(dto,entity);
        return Result.success(categoryService.updateById(entity));
    }

    @DeleteMapping("/{categoryId}")
//    @Operation(summary = "删除分类")
    @Log(module = "物料分类管理", businessType = BusinessType.DELETE, bizType = "'material_category'", bizId = "#categoryId", action = LogActions.MAT_CATEGORY_DELETE)
    @SaCheckPermission("inventory:category:remove")
    public Result<Boolean> delete(@PathVariable Long categoryId) {
        return Result.success(categoryService.deleteWithCheck(categoryId));
    }

    @PutMapping("/{categoryId}/status")
//    @Operation(summary = "更新分类状态")
    @Log(module = "物料分类管理", businessType = BusinessType.UPDATE, bizType = "'material_category'", bizId = "#categoryId", action = LogActions.MAT_CATEGORY_STATUS)
    @SaCheckPermission("inventory:category:edit")
    public Result<Boolean> updateStatus(@PathVariable Long categoryId,
                                        @RequestParam String status) {
        return Result.success(categoryService.updateStatus(categoryId, status));
    }
}
