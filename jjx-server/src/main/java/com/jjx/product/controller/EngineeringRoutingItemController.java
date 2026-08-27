package com.jjx.product.controller;

import com.jjx.common.core.result.Result;
import com.jjx.engineering.domain.entity.EngineeringRoutingItem;
import com.jjx.product.mapper.EngineeringRoutingItemMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 产品路线明细控制器
 */
@Tag(name = "产品路线明细管理", description = "产品路线明细相关接口")
@RestController
@RequestMapping("/engineering/routing-items")
@RequiredArgsConstructor
@Validated
public class EngineeringRoutingItemController {

    private final EngineeringRoutingItemMapper routingItemMapper;

    @Operation(summary = "根据路线ID获取明细列表")
    @GetMapping("/routing/{routingId}")
    public Result<List<EngineeringRoutingItem>> getByRoutingId(
            @Parameter(description = "路线ID", required = true)
            @PathVariable @NotNull Long routingId) {
        List<EngineeringRoutingItem> list = routingItemMapper.selectByRoutingId(routingId);
        return Result.success(list);
    }

    @Operation(summary = "获取明细详情")
    @GetMapping("/{detailId}")
    public Result<EngineeringRoutingItem> getById(
            @Parameter(description = "明细ID", required = true)
            @PathVariable @NotNull Long detailId) {
        EngineeringRoutingItem item = routingItemMapper.selectById(detailId);
        return Result.success(item);
    }

    @Operation(summary = "添加路线明细")
    @PostMapping
    public Result<Void> add(@Valid @RequestBody EngineeringRoutingItem item) {
        routingItemMapper.insert(item);
        return Result.success();
    }

    @Operation(summary = "更新路线明细")
    @PutMapping("/{itemId}")
    public Result<Void> update(
            @Parameter(description = "明细ID", required = true)
            @PathVariable @NotNull Long itemId,
            @Valid @RequestBody EngineeringRoutingItem item) {
        item.setItemId(itemId);
        routingItemMapper.updateById(item);
        return Result.success();
    }

    @Operation(summary = "删除路线明细")
    @DeleteMapping("/{detailId}")
    public Result<Void> delete(
            @Parameter(description = "明细ID", required = true)
            @PathVariable @NotNull Long detailId) {
        routingItemMapper.deleteById(detailId);
        return Result.success();
    }

    @Operation(summary = "批量保存路线明细")
    @PostMapping("/batch")
    public Result<Void> batchSave(
            @Parameter(description = "路线ID", required = true)
            @RequestParam @NotNull Long routingId,
            @RequestBody List<EngineeringRoutingItem> items) {
        // 先删除原有明细
        routingItemMapper.deleteByRoutingId(routingId);
        // 批量插入
        if (items != null && !items.isEmpty()) {
            routingItemMapper.insert(items);
        }
        return Result.success();
    }

    @Operation(summary = "调整工序顺序")
    @PutMapping("/reorder")
    public Result<Void> reorder(
            @Parameter(description = "路线ID", required = true)
            @RequestParam @NotNull Long routingId,
            @RequestBody List<Long> detailIds) {
        List<EngineeringRoutingItem> items = routingItemMapper.selectByRoutingId(routingId);
        for (int i = 0; i < items.size(); i++) {
            EngineeringRoutingItem item = items.get(i);
            item.setProcessOrder(i + 1);
            routingItemMapper.updateById(item);
        }
        return Result.success();
    }
}