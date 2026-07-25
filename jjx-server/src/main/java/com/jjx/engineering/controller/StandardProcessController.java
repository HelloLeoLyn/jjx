package com.jjx.engineering.controller;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.common.core.result.Result;
import com.jjx.engineering.service.IStandardProcessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@Tag(name = "工程标准工序") @RestController
@RequestMapping("/engineering/standard-process") @RequiredArgsConstructor
public class StandardProcessController {
    private final IStandardProcessService standardProcessService;

    @Operation(summary = "标准工序列表")
    @SaCheckPermission("engineering:standard-process:view") @GetMapping("/page")
    public Result<?> page() { return Result.success(standardProcessService.listPage(null)); }

    @Operation(summary = "启用的标准工序列表（下拉框用）")
    @SaCheckPermission("engineering:standard-process:view") @GetMapping("/enabled")
    public Result<?> enabled() { return Result.success(standardProcessService.list()); }
}
