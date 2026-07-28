package com.jjx.engineering.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.common.core.page.PageResult;
import com.jjx.common.core.result.Result;
import com.jjx.engineering.domain.entity.EngineeringBase;
import com.jjx.engineering.service.EngineeringBaseService;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.annotation.Log;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "工程管理")
@RestController
@RequestMapping("/engineering")
@RequiredArgsConstructor
public class EngineeringController {

    private final EngineeringBaseService engineeringBaseService;

    @Operation(summary = "分页查询工程记录")
    @GetMapping("/page")
    public Result<PageResult<EngineeringBase>> page(@RequestParam(defaultValue = "1") int pageNum,
                                                     @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(engineeringBaseService.pageQuery(pageNum, pageSize));
    }

    @Operation(summary = "查询工程详情")
    @GetMapping("/{id}")
    public Result<EngineeringBase> getById(@PathVariable Long id) {
        return Result.success(engineeringBaseService.getById(id));
    }

    @Operation(summary = "新增工程记录")
    @Log(module = "工程管理", businessType = BusinessType.INSERT)
    @SaCheckPermission("engineering:add")
    @PostMapping
    public Result<Void> save(@Validated @RequestBody EngineeringBase entity) {
        engineeringBaseService.save(entity);
        return Result.success();
    }

    @Operation(summary = "更新工程记录")
    @Log(module = "工程管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("engineering:edit")
    @PutMapping
    public Result<Void> update(@Validated @RequestBody EngineeringBase entity) {
        engineeringBaseService.update(entity);
        return Result.success();
    }

    @Operation(summary = "删除工程记录")
    @Log(module = "工程管理", businessType = BusinessType.DELETE)
    @SaCheckPermission("engineering:delete")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        engineeringBaseService.deleteById(id);
        return Result.success();
    }
}
