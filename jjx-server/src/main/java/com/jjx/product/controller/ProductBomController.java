package com.jjx.product.controller;

import com.jjx.common.core.page.PageResult;
import com.jjx.common.core.result.Result;
import com.jjx.framework.common.controller.BaseController;
import com.jjx.product.domain.dto.ProductBomDTO;
import com.jjx.product.domain.dto.UpdateBomStatusDTO;
import com.jjx.product.domain.entity.ProductBom;
import com.jjx.product.domain.entity.ProductBomItem;
import com.jjx.product.domain.query.ProductBomQuery;
import com.jjx.product.domain.vo.ProductBomVO;
import com.jjx.product.enums.ProductEnums;
import com.jjx.product.service.IProductBomService;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.annotation.Log;
import cn.dev33.satoken.annotation.SaCheckPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/product/bom")
@RequiredArgsConstructor
public class ProductBomController extends BaseController {
    private final IProductBomService productBomService;


    /**
     * 获取BOM列表
     */
    @GetMapping("/page")
    public Result<PageResult<ProductBomVO>> listPage(ProductBomQuery query) {
        return Result.success(productBomService.listPage(query));
    }

    /**
     * 获取BOM详情
     */
    @GetMapping("/{bomId}")
    public Result<ProductBomVO> getInfo(@PathVariable Long bomId) {
        ProductBomVO bom = productBomService.getBomDetail(bomId);
        return Result.success(bom);
    }

    /**
     * 新增BOM
     */
    @PostMapping
    @Log(module = "产品BOM管理", businessType = BusinessType.INSERT)
    @SaCheckPermission("engineering:bom:add")
    public Result<Void> add(@Validated @RequestBody ProductBomDTO dto) {
        boolean result = productBomService.createBom(dto);
        return result ? Result.success() : Result.error();
    }

    /**
     * 修改BOM
     */
    @PutMapping
    @Log(module = "产品BOM管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("engineering:bom:edit")
    public Result<Void> edit(@Validated @RequestBody ProductBomDTO dto) {
        boolean result = productBomService.updateBom(dto);
        return result ? Result.success() : Result.error();
    }

    /**
     * 删除BOM
     */
    @DeleteMapping("/{bomId}")
    @Log(module = "产品BOM管理", businessType = BusinessType.DELETE)
    @SaCheckPermission("engineering:bom:delete")
    public Result<Void> remove(@PathVariable Long bomId) {
        boolean result = productBomService.removeBomWithItems(bomId);
        return result ? Result.success() : Result.error();
    }

    /**
     * 审批BOM
     */
    @PutMapping("/approve/{bomId}")
    @Log(module = "产品BOM管理", businessType = BusinessType.APPROVE)
    @SaCheckPermission("engineering:bom:approve")
    public Result<Void> approve(@PathVariable Long bomId, @Validated @RequestBody UpdateBomStatusDTO dto) {
        boolean result = productBomService.approve(dto);
        return result ? Result.success() : Result.error();
    }

    /**
     * 审批BOM
     */
    @PutMapping("/reject/{bomId}")
    @Log(module = "产品BOM管理", businessType = BusinessType.APPROVE)
    @SaCheckPermission("engineering:bom:reject")
    public Result<Void> reject(@PathVariable Long bomId, @Validated @RequestBody UpdateBomStatusDTO dto) {
        boolean result = productBomService.reject(dto);
        return result ? Result.success() : Result.error();
    }

    /**
     * 设置默认BOM
     */
    @PutMapping("/setDefault/{bomId}")
    @Log(module = "产品BOM管理", businessType = BusinessType.UPDATE)
    @SaCheckPermission("engineering:bom:edit")
    public Result<Void> setDefault(@PathVariable Long bomId) {
        boolean result = productBomService.setDefaultBom(bomId);
        return result ? Result.success() : Result.error();
    }

    /**
     * 获取产品的默认BOM
     */
    @GetMapping("/default/{productId}")
    public Result<ProductBom> getDefaultBom(@PathVariable Long productId) {
        ProductBom bom = productBomService.getDefaultBomByProductId(productId);
        return Result.success(bom);
    }

    /**
     * 获取BOM明细列表
     */
    @GetMapping("/items/{bomId}")
    public Result<List<ProductBomItem>> getBomItems(@PathVariable Long bomId) {
        List<ProductBomItem> items = productBomService.getBomItems(bomId);
        return Result.success(items);
    }

    /**
     * 获取产品已审批的BOM列表（用于产品新增/编辑时选择）
     */
    @GetMapping("/approved/{productId}")
    public Result<List<ProductBomVO>> getApprovedBomList(@PathVariable Long productId) {
        ProductBomQuery query = new ProductBomQuery();
        query.setProductId(productId);
        query.setApproveStatus(String.valueOf(ProductEnums.BomStatus.APPROVED.getValue()));
        List<ProductBomVO> list = productBomService.getBomList(query);
        return Result.success(list);
    }

    /**
     * 计算BOM成本
     */
    @PostMapping("/calculateCost/{bomId}")
    public Result<Void> calculateCost(@PathVariable Long bomId) {
        productBomService.calculateBomCost(bomId);
        return Result.success();
    }
}
