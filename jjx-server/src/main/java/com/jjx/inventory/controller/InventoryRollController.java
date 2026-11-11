package com.jjx.inventory.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.jjx.common.core.result.Result;
import com.jjx.inventory.domain.InventoryTransaction;
import com.jjx.inventory.dto.save.RollReturnSaveDTO;
import com.jjx.inventory.service.InventoryRollService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/inventory/roll")
@RequiredArgsConstructor
public class InventoryRollController {
    private final InventoryRollService service;

    @GetMapping("/area")
    @SaCheckPermission(value = {"inventory:stock:view", "inventory:inbound:add"}, mode = SaMode.OR)
    public Result<BigDecimal> area(@RequestParam BigDecimal widthMm, @RequestParam BigDecimal lengthM) {
        return Result.success(service.area(widthMm, lengthM));
    }

    @GetMapping("/flow")
    @SaCheckPermission("inventory:transaction:view")
    public Result<List<InventoryTransaction>> flow(@RequestParam String rollNo) {
        return Result.success(service.flow(rollNo));
    }

    @PostMapping("/return")
    @SaCheckPermission({"inventory:inbound:add", "inventory:inbound:edit"})
    public Result<Long> returnRoll(@Valid @RequestBody RollReturnSaveDTO input) {
        return Result.success(service.returnRoll(input));
    }

    @PostMapping("/reverse-return/{itemId}")
    @SaCheckPermission({"inventory:inbound:add", "inventory:inbound:edit"})
    public Result<Long> reverseReturn(@PathVariable Long itemId, @RequestParam String reason) {
        return Result.success(service.reverseReturn(itemId, reason));
    }

    @PostMapping("/reverse-issue/{itemId}")
    @SaCheckPermission({"inventory:outbound:add", "inventory:outbound:edit"})
    public Result<Long> reverseIssue(@PathVariable Long itemId, @RequestParam String reason) {
        return Result.success(service.reverseIssue(itemId, reason));
    }
}
