package com.jjx.inventory.controller;

import com.jjx.common.core.result.Result;
import com.jjx.inventory.service.OrderMaterialReserveService;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.annotation.Log;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 订单材料预占 Controller（094定稿：确认前手动预占原料）
 */
@RestController
@RequestMapping("/inventory/material-reserve")
@RequiredArgsConstructor
@Tag(name = "材料预占", description = "订单材料预占（094：确认前手动预占原料，解决已审核未确认不占料盲区）")
public class OrderMaterialReserveController {

    private final OrderMaterialReserveService reserveService;

    @PostMapping("/reserve/{orderId}")
    @Operation(summary = "材料预占（按BOM展开原料，天数1~7默认3）")
    @Log(module = "材料预占", businessType = BusinessType.UPDATE, bizType = "'order'", bizId = "#orderId")
    @SaCheckPermission("inventory:alert:edit")
    public Result<Map<String, Object>> reserve(@PathVariable Long orderId,
                                               @RequestParam(required = false) Integer days) {
        return Result.success(reserveService.reserveForOrder(orderId, days));
    }

    @PostMapping("/extend/{orderId}")
    @Operation(summary = "延迟预占（每次+3天）")
    @Log(module = "材料预占", businessType = BusinessType.UPDATE, bizType = "'order'", bizId = "#orderId")
    @SaCheckPermission("inventory:alert:edit")
    public Result<Void> extend(@PathVariable Long orderId) {
        reserveService.extendReserve(orderId);
        return Result.success();
    }

    @PostMapping("/release/{orderId}")
    @Operation(summary = "释放预占（取消/完成/手动）")
    @Log(module = "材料预占", businessType = BusinessType.UPDATE, bizType = "'order'", bizId = "#orderId")
    @SaCheckPermission("inventory:alert:edit")
    public Result<Void> release(@PathVariable Long orderId, @RequestParam(required = false) String reason) {
        reserveService.releaseByOrder(orderId, reason);
        return Result.success();
    }

    @GetMapping("/info/{orderId}")
    @Operation(summary = "查询订单预占信息")
    public Result<Map<String, Object>> info(@PathVariable Long orderId) {
        return Result.success(reserveService.getOrderReserveInfo(orderId));
    }

    @PostMapping("/process-timeout")
    @Operation(summary = "预占超时处理（快到期提醒+到期自动释放，定时任务调用）")
    @SaCheckPermission("inventory:alert:edit")
    public Result<Void> processTimeout() {
        reserveService.processTimeout();
        return Result.success();
    }
}
