package com.jjx.inventory.controller;

import com.jjx.common.core.result.Result;
import com.jjx.inventory.domain.ProductStock;
import com.jjx.inventory.service.ProductStockService;
import cn.dev33.satoken.annotation.SaCheckPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 产品库存 Controller（产品维度独立记账）
 * 概念红线：产品≠物料；产品看库存直接查 product_stock 表，
 * 与物料库存（inventory_stock）各自独立记账
 */
@RestController
@RequestMapping("/inventory/product-stock")
@RequiredArgsConstructor
@Tag(name = "产品库存", description = "产品库存查询接口（产品维度独立记账）")
public class ProductStockController {

    private final ProductStockService productStockService;

    @GetMapping("/list")
    @Operation(summary = "查询所有产品库存（产品维度看库存）")
    @SaCheckPermission("inventory:stock:view")
    public Result<List<ProductStock>> list() {
        return Result.success(productStockService.listAll());
    }

    @GetMapping("/{productId}")
    @Operation(summary = "按产品ID查询产品库存")
    @SaCheckPermission("inventory:stock:view")
    public Result<ProductStock> getByProductId(@PathVariable Long productId) {
        return Result.success(productStockService.getByProductId(productId));
    }
}
