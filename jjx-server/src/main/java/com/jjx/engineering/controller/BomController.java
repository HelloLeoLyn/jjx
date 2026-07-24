package com.jjx.engineering.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.common.core.page.PageResult;
import com.jjx.common.core.result.Result;
import com.jjx.engineering.service.IBomService;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.annotation.Log;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "工程BOM管理")
@RestController
@RequestMapping("/engineering/bom")
@RequiredArgsConstructor
public class BomController {

    private final IBomService bomService;

    @Operation(summary = "获取BOM列表")
    @SaCheckPermission("engineering:bom:view")
    @GetMapping("/page")
    public Result<PageResult<?>> page() {
        return Result.success(bomService.listPage(null));
    }

    @Operation(summary = "审核BOM")
    @Log(module = "工程BOM管理", businessType = BusinessType.APPROVE)
    @SaCheckPermission("engineering:bom:approve")
    @PutMapping("/approve/{bomId}")
    public Result<Void> approve(@PathVariable Long bomId, @RequestBody Map<String, Object> dto) {
        return Result.success();
    }

    @Operation(summary = "驳回BOM")
    @Log(module = "工程BOM管理", businessType = BusinessType.APPROVE)
    @SaCheckPermission("engineering:bom:reject")
    @PutMapping("/reject/{bomId}")
    public Result<Void> reject(@PathVariable Long bomId, @RequestBody Map<String, Object> dto) {
        return Result.success();
    }
}
