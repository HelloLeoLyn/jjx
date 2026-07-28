package com.jjx.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jjx.common.core.page.PageResult;
import com.jjx.common.core.result.Result;
import com.jjx.framework.common.controller.BaseController;
import com.jjx.system.domain.dto.SysDictDTO;
import com.jjx.system.domain.dto.SysDictItemDTO;
import com.jjx.system.domain.vo.SysDictItemVO;
import com.jjx.system.domain.vo.SysDictVO;
import com.jjx.system.service.SysDictService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 系统模块 - 字典管理控制器
 */
@Tag(name = "系统模块 - 字典管理")
@RestController
@RequestMapping("/system/dict")
@RequiredArgsConstructor
public class SysDictController extends BaseController {

    private final SysDictService dictService;

    // ==================== 字典类型接口 ====================

    @Operation(summary = "分页查询字典类型列表")
    @SaCheckPermission("system:dict:list")
    @GetMapping("/list")
    public Result<PageResult<SysDictVO>> list(SysDictDTO dto) {
        Page<SysDictVO> page = dictService.selectDictList(dto, getPageNum(), getPageSize());
        return Result.success(getDataTable(page.getRecords(),page.getTotal()));
    }

    @Operation(summary = "查询所有字典类型列表")
    @SaCheckPermission("system:dict:list")
    @GetMapping("/all")
    public Result<List<SysDictVO>> getAllDicts() {
        return Result.success(dictService.selectAllDictList());
    }

    @Operation(summary = "获取字典类型详情")
    @SaCheckPermission("system:dict:query")
    @GetMapping("/{dictId}")
    public Result<SysDictVO> getDict(@PathVariable Long dictId) {
        return Result.success(dictService.selectDictById(dictId));
    }

    @Operation(summary = "新增字典类型")
    @SaCheckPermission("system:dict:add")
    @PostMapping
    public Result<Void> addDict(@Validated @RequestBody SysDictDTO dto) {
        return toAjax(dictService.insertDict(dto));
    }

    @Operation(summary = "修改字典类型")
    @SaCheckPermission("system:dict:edit")
    @PutMapping("/{dictId}")
    public Result<Void> updateDict(@PathVariable Long dictId, @Validated @RequestBody SysDictDTO dto) {
        dto.setDictId(dictId);
        return toAjax(dictService.updateDict(dto));
    }

    @Operation(summary = "删除字典类型")
    @SaCheckPermission("system:dict:delete")
    @DeleteMapping("/{dictIds}")
    public Result<Void> deleteDict(@PathVariable Long[] dictIds) {
        return toAjax(dictService.deleteDictByIds(dictIds));
    }

    @Operation(summary = "启用/禁用字典类型")
    @SaCheckPermission("system:dict:edit")
    @PutMapping("/{dictId}/status")
    public Result<Void> changeDictStatus(@PathVariable Long dictId, @RequestParam Integer isActive) {
        return toAjax(dictService.changeDictStatus(dictId, isActive));
    }

    // ==================== 字典项接口 ====================

    @Operation(summary = "根据字典编码获取字典项列表")
    @SaCheckPermission("system:dict:query")
    @GetMapping("/code/{dictCode}")
    public Result<List<SysDictItemVO>> getDictItems(@PathVariable String dictCode) {
        return Result.success(dictService.selectActiveItemsByDictCode(dictCode));
    }

    @Operation(summary = "新增字典项")
    @SaCheckPermission("system:dict:add")
    @PostMapping("/item")
    public Result<Void> addDictItem(@Validated @RequestBody SysDictItemDTO dto) {
        return toAjax(dictService.insertDictItem(dto));
    }

    @Operation(summary = "修改字典项")
    @SaCheckPermission("system:dict:edit")
    @PutMapping("/item/{itemId}")
    public Result<Void> updateDictItem(@PathVariable Long itemId, @Validated @RequestBody SysDictItemDTO dto) {
        dto.setItemId(itemId);
        return toAjax(dictService.updateDictItem(dto));
    }

    @Operation(summary = "删除字典项")
    @SaCheckPermission("system:dict:delete")
    @DeleteMapping("/item/{itemIds}")
    public Result<Void> deleteDictItem(@PathVariable Long[] itemIds) {
        return toAjax(dictService.deleteDictItemByIds(itemIds));
    }

    @Operation(summary = "启用/禁用字典项")
    @SaCheckPermission("system:dict:edit")
    @PutMapping("/item/{itemId}/status")
    public Result<Void> changeDictItemStatus(@PathVariable Long itemId, @RequestParam Integer isActive) {
        return toAjax(dictService.changeDictItemStatus(itemId, isActive));
    }

    @Operation(summary = "导出字典")
    @SaCheckPermission("system:dict:export")
    @GetMapping("/export/{dictCode}")
    public Result<List<SysDictItemVO>> exportDict(@PathVariable String dictCode) {
        return Result.success(dictService.selectActiveItemsByDictCode(dictCode));
    }

    @Operation(summary = "按分组列表")
    @GetMapping("/group/{dictGroup}")
    public Result<List<SysDictVO>> getByGroup(@PathVariable String dictGroup) {
        return Result.success(dictService.selectDictListByGroup(dictGroup));
    }
}
