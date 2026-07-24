package com.jjx.engineering.controller;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.common.core.result.Result;
import com.jjx.engineering.service.IRoutingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@Tag(name = "工程工艺路线") @RestController
@RequestMapping("/engineering/routing") @RequiredArgsConstructor
public class RoutingController {
    private final IRoutingService routingService;
    @Operation(summary = "工艺路线列表")
    @SaCheckPermission("engineering:routing:view") @GetMapping("/page")
    public Result<?> page() { return Result.success(routingService.listPage(null)); }
}
