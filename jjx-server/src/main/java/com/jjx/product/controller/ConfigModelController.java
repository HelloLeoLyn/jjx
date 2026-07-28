package com.jjx.product.controller;

import com.jjx.common.core.result.Result;
import com.jjx.product.service.IConfigModelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "产品配置模型")
@RestController
@RequestMapping("/engineering/config")
@RequiredArgsConstructor
public class ConfigModelController {
    private final IConfigModelService configService;

    @Operation(summary = "配置模型列表")
    @GetMapping
    public Result<?> list() { return Result.success(configService.listPage(null)); }

    @Operation(summary = "配置模型分页")
    @GetMapping("/page")
    public Result<?> page() { return Result.success(configService.listPage(null)); }
}
