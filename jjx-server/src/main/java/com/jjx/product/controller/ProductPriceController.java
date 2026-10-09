package com.jjx.product.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.jjx.common.core.page.PageResult;
import com.jjx.common.core.result.Result;
import com.jjx.product.domain.dto.ProductPriceUpdateDTO;
import com.jjx.product.domain.query.ProductQuery;
import com.jjx.product.domain.vo.ProductEditVO;
import com.jjx.product.domain.vo.ProductPriceVO;
import com.jjx.product.service.ProductPriceService;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.annotation.Log;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/product/prices")
@RequiredArgsConstructor
public class ProductPriceController {
    private final ProductPriceService priceService;

    @GetMapping
    @SaCheckPermission(ProductPriceService.VIEW_PERMISSION)
    public Result<PageResult<ProductPriceVO>> page(ProductQuery query) {
        return Result.success(priceService.page(query));
    }

    @GetMapping("/{productId}")
    @SaCheckPermission(ProductPriceService.VIEW_PERMISSION)
    public Result<ProductPriceVO> get(@PathVariable Long productId) {
        return Result.success(priceService.get(productId));
    }

    @PutMapping("/{productId}")
    @SaCheckPermission(value = {ProductPriceService.VIEW_PERMISSION, ProductPriceService.EDIT_PERMISSION}, mode = SaMode.AND)
    @Log(module = "产品价格", businessType = BusinessType.UPDATE, bizType = "'product'", bizId = "#productId",
            detail = "#result.data.detailMessage", action = "维护产品价格")
    public Result<ProductEditVO> update(@PathVariable Long productId, @Validated @RequestBody ProductPriceUpdateDTO dto) {
        return Result.success(priceService.update(productId, dto));
    }
}
