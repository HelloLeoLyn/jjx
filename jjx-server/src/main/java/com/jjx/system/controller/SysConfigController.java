package com.jjx.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.common.core.result.Result;
import com.jjx.system.domain.entity.SysConfig;
import com.jjx.system.service.SysConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "系统配置")
@RestController
@RequestMapping("/system/config")
@RequiredArgsConstructor
public class SysConfigController {

    private final SysConfigService configService;

    @GetMapping("/list")
    @SaCheckPermission("system:config:view")
    @Operation(summary = "配置列表")
    public Result<List<SysConfig>> list() {
        return Result.success(configService.listAll());
    }

    @GetMapping("/group/{group}")
    @SaCheckPermission("system:config:view")
    @Operation(summary = "按分组查询")
    public Result<List<SysConfig>> listByGroup(@PathVariable String group) {
        return Result.success(configService.listByGroup(group));
    }

    @GetMapping("/value/{key}")
    @SaCheckPermission("system:config:view")
    @Operation(summary = "获取配置值")
    public Result<String> getValue(@PathVariable String key) {
        return Result.success(configService.getValue(key));
    }

    @PutMapping("/{id}")
    @SaCheckPermission("system:config:edit")
    @Operation(summary = "更新配置值")
    public Result<Void> updateValue(@PathVariable Long id, @RequestParam String value) {
        configService.updateValue(id, value);
        return Result.success();
    }
}
