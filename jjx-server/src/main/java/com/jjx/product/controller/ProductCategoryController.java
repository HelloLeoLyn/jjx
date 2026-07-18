package com.jjx.product.controller;

import com.jjx.common.core.result.Result;
import com.jjx.product.domain.entity.ProductCategory;
import com.jjx.product.domain.query.ProductCategoryQuery;
import com.jjx.product.domain.vo.ProductCategoryTreeVo;
import com.jjx.product.service.IProductCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 产品分类Controller
 */
@RestController
@RequestMapping("/product/category")
@RequiredArgsConstructor
public class ProductCategoryController {

    private final IProductCategoryService productCategoryService;

    /**
     * 获取分类树形结构
     */
    @GetMapping("/tree")
    public Result<List<ProductCategoryTreeVo>> tree(ProductCategoryQuery query) {
        List<ProductCategoryTreeVo> tree = productCategoryService.getCategoryTree(query);
        return Result.success(tree);
    }

    /**
     * 获取分类列表
     */
    @GetMapping("/list")
    public Result<List<ProductCategory>> list(ProductCategoryQuery query) {
        List<ProductCategory> list = productCategoryService.getCategoryList(query);
        return Result.success(list);
    }

    /**
     * 获取分类详情
     */
    @GetMapping("/{categoryId}")
    public Result<ProductCategory> getInfo(@PathVariable Long categoryId) {
        ProductCategory category = productCategoryService.getById(categoryId);
        return Result.success(category);
    }

    /**
     * 新增分类
     */
    @PostMapping
    public Result<Void> add(@Validated @RequestBody ProductCategory category) {
        boolean result = productCategoryService.createCategory(category);
        return result ? Result.success() : Result.error();
    }

    /**
     * 修改分类
     */
    @PutMapping
    public Result<Void> edit(@Validated @RequestBody ProductCategory category) {
        boolean result = productCategoryService.updateCategory(category);
        return result ? Result.success() : Result.error();
    }

    /**
     * 删除分类
     */
    @DeleteMapping("/{categoryId}")
    public Result<Void> remove(@PathVariable Long categoryId) {
        boolean result = productCategoryService.deleteCategory(categoryId);
        return result ? Result.success() : Result.error();
    }

    /**
     * 检查分类编码是否唯一
     */
    @GetMapping("/checkCategoryCodeUnique")
    public Result<Boolean> checkCategoryCodeUnique(String categoryCode) {
        boolean result = productCategoryService.checkCategoryCodeUnique(categoryCode);
        return Result.success(result);
    }

    /**
     * 检查分类名称是否唯一
     */
    @GetMapping("/checkCategoryNameUnique")
    public Result<Boolean> checkCategoryNameUnique(String categoryName) {
        boolean result = productCategoryService.checkCategoryNameUnique(categoryName);
        return Result.success(result);
    }
}
