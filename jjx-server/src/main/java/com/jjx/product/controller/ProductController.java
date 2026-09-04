package com.jjx.product.controller;

import com.jjx.common.constant.LogActions;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.jjx.common.core.page.PageResult;
import com.jjx.common.core.result.Result;
import com.jjx.framework.common.controller.BaseController;
import com.jjx.product.domain.dto.ProductDTO;
import com.jjx.product.domain.dto.ProductUpdateDTO;
import com.jjx.product.domain.entity.Product;
import com.jjx.product.domain.query.ProductQuery;
import com.jjx.product.domain.vo.ProductEditVO;
import com.jjx.product.domain.vo.ProductFullVO;
import com.jjx.product.domain.vo.ProductVo;
import com.jjx.product.service.IProductService;
import com.jjx.product.service.ProductCodeService;
import com.jjx.product.service.impl.ProductServiceImpl;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.annotation.Log;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 产品Controller
 */
@RestController
@RequestMapping("/product")
@RequiredArgsConstructor
public class ProductController extends BaseController {

    private final IProductService productService;
    private final ProductCodeService productCodeService;
    private final ProductServiceImpl productServiceImpl;

    /**
     * 获取产品总数
     */
    @GetMapping("/count")
    public Result<Long> count() {
        return Result.success(productService.count());
    }

    /**
     * 获取产品列表
     */
    @GetMapping("/page")
    public Result<PageResult<ProductVo>> page(ProductQuery query) {
        PageResult<ProductVo> productPage = productService.getProductFullPage(query);
        return Result.success(productPage);
    }

    /**
     * 获取产品列表
     */
    @GetMapping("/list")
    public Result<List<ProductVo>> list(ProductQuery query) {
        List<ProductVo> productList = productService.getProductList(query);
        return Result.success(productList);
    }
    /**
     * 获取产品详情
     */
    @GetMapping("/{productId}")
    public Result<ProductVo> getInfo(@PathVariable Long productId) {
        ProductVo product = productService.getProductDetail(productId);
        return Result.success(product);
    }
    /**
     * 获取产品详情full
     */
    @GetMapping("/{productId}/full")
    public Result<ProductFullVO> full(@PathVariable Long productId) {
        ProductFullVO product = productService.getFullProductDetail(productId);
        return Result.success(product);
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "产品业务流转附件聚合（询价/报价/订单）")
    @GetMapping("/{productId}/biz-attachments")
    public Result<List<Map<String, Object>>> bizAttachments(@PathVariable Long productId) {
        return Result.success(productServiceImpl.getBizAttachments(productId));
    }

    /**
     * 停产
     */
    @PutMapping("/obsolete/{id}")
    @Log(module = "产品管理", businessType = BusinessType.UPDATE, bizType = "'product'", bizId = "#id", action = LogActions.PRODUCT_OBSOLETE)
    @SaCheckPermission("product:product:obsolete")
    public Result<Void> obsolete(@PathVariable Long id) {
        return toAjax(productService.obsoleteProduct(id));
    }

    /**
     * 取消（取消审核/取消发布）
     */
    @PutMapping("/cancel/{id}")
    @Log(module = "产品管理", businessType = BusinessType.UPDATE, bizType = "'product'", bizId = "#id", action = LogActions.PRODUCT_CANCEL)
    @SaCheckPermission("product:product:edit")
    public Result<Void> cancel(@PathVariable Long id) {
        return toAjax(productService.cancelProduct(id));
    }

    /**
     * 新增产品
     */
    @PostMapping
    @Log(module = "产品管理", businessType = BusinessType.INSERT,
         bizId = "#result.data", bizType = "'product'", action = LogActions.PRODUCT_CREATE)
    @SaCheckPermission("product:create")
    public Result<Long> add(@Validated @RequestBody ProductDTO productDTO) {
        Long productId = productService.addProduct(productDTO);
        return productId != null ? Result.success(productId) : Result.error();
    }

    /**
     * 修改产品
     */
    @PutMapping
    @Log(module = "产品管理", businessType = BusinessType.UPDATE,
         bizId = "#productDTO.productId", bizType = "'product'",
         detail = "#result.data.detailMessage", action = LogActions.PRODUCT_EDIT)
    @SaCheckPermission("product:edit")
    public Result<ProductEditVO> edit(@Validated @RequestBody ProductDTO productDTO) {
        if (!productService.checkProductCodeUnique(productDTO.getProductCode(), productDTO.getProductId())) {
            return Result.error("修改产品'" + productDTO.getProductName() + "'失败，产品编码已存在");
        }
        if (!productService.checkProductNameUnique(productDTO.getProductName(), productDTO.getProductId())) {
            return Result.error("修改产品'" + productDTO.getProductName() + "'失败，产品名称已存在");
        }
        Product product = new Product();
        org.springframework.beans.BeanUtils.copyProperties(productDTO, product);
        // Double → BigDecimal 手动转换（BeanUtils 跨类型不复制，价格会静默丢失）
        if (productDTO.getBasePrice() != null) {
            product.setBasePrice(java.math.BigDecimal.valueOf(productDTO.getBasePrice()));
        }
        if (productDTO.getCostPrice() != null) {
            product.setCostPrice(java.math.BigDecimal.valueOf(productDTO.getCostPrice()));
        }
        // 变更明细必须在 updateById 之前采集（此时库里还是旧值）
        String detailMessage = productService.buildEditDetail(productDTO);
        boolean result = productService.updateById(product);
        ProductEditVO vo = new ProductEditVO();
        vo.setSuccess(result);
        vo.setDetailMessage(detailMessage);
        return result ? Result.success(vo) : Result.error();
    }
    /**
     * 删除产品
     */
    @DeleteMapping("/{productId}")
    @Log(module = "产品管理", businessType = BusinessType.DELETE,
         bizId = "#productId", bizType = "'product'", action = LogActions.PRODUCT_DELETE)
    @SaCheckPermission("product:delete")
    public Result<Void> remove(@PathVariable Long productId) {
        boolean result = productService.removeById(productId);
        return result ? Result.success() : Result.error();
    }

    /**
     * 发布产品
     */
    @PutMapping("/release/{productId}")
    @Log(module = "产品管理", businessType = BusinessType.UPDATE,
         bizId = "#productId", bizType = "'product'", action = LogActions.PRODUCT_RELEASE)
    @SaCheckPermission("product:status:release")
    public Result<Void> release(@PathVariable Long productId) {
        // 执行发布，验证逻辑在service层处理
        boolean result = productService.releaseProduct(productId);
        return result ? Result.success() : Result.error();
    }
    /**
     * 提交审核
     */
    @PutMapping("/submit/{productId}")
    @Log(module = "产品管理", businessType = BusinessType.UPDATE,
         bizId = "#productId", bizType = "'product'", action = LogActions.PRODUCT_SUBMIT)
    @SaCheckPermission("product:status:submit")
    public Result<Void> submit(@PathVariable Long productId) {
        // 执行发布，验证逻辑在service层处理
        boolean result = productService.submitProduct(productId);
        return result ? Result.success() : Result.error();
    }

    /**
     * 审核通过
     */
    @PutMapping("/approve/{productId}")
    @Log(module = "产品管理", businessType = BusinessType.UPDATE,
         bizId = "#productId", bizType = "'product'", action = LogActions.PRODUCT_APPROVE)
    @SaCheckPermission("product:status:approve")
    public Result<Void> approve(@PathVariable Long productId, ProductUpdateDTO dto) {
        // 执行发布，验证逻辑在service层处理
        boolean result = productService.approveProduct(dto);
        return result ? Result.success() : Result.error();
    }
    /**
     * 驳回审核
     */
    @PutMapping("/reject/{productId}")
    @Log(module = "产品管理", businessType = BusinessType.UPDATE,
         bizId = "#productId", bizType = "'product'", action = LogActions.PRODUCT_REJECT)
    @SaCheckPermission("product:status:reject")
    public Result<Void> reject(@PathVariable Long productId, ProductUpdateDTO dto) {
        // 执行发布，验证逻辑在service层处理
        boolean result = productService.rejectProduct(dto);
        return result ? Result.success() : Result.error();
    }
    /**
     * 检查产品编码是否唯一
     */
    @GetMapping("/product-code/{productCode}/unique")
    public Result<Boolean> checkProductCodeUnique(@PathVariable String productCode) {
        boolean result = productService.checkProductCodeUnique(productCode,null);
        return Result.success(result);
    }

    /**
     * 检查产品名称是否唯一
     */
    @GetMapping("/checkProductNameUnique")
    public Result<Boolean> checkProductNameUnique(String productName, Long productId) {
        boolean result = productService.checkProductNameUnique(productName, productId);
        return Result.success(result);
    }

    /**
     * 搜索产品
     */
    @GetMapping("/search")
    public Result<List<Product>> search(String keyword, Long customerId) {
        List<Product> products = productService.searchProducts(keyword, customerId);
        return Result.success(products);
    }

    /**
     * 根据分类获取产品列表
     */
    @GetMapping("/byCategory/{categoryId}")
    public Result<List<Product>> getByCategory(@PathVariable Long categoryId) {
        List<Product> products = productService.getProductsByCategory(categoryId);
        return Result.success(products);
    }

    /**
     * 根据分类获取产品列表
     */
    @GetMapping("/product-code/{categoryId}")
    public Result<String> getByCategory(@PathVariable String categoryId) {
        return Result.success( productService.getProductCode(categoryId));
    }

    /**
     * 根据客户ID生成流水号（3位）
     */
    @GetMapping("/serial-no/{customerId}")
    public Result<String> generateSerialNo(@PathVariable Long customerId) {
        return Result.success(productService.generateSerialNo(customerId));
    }

    /**
     * 统一流水号接口（2026-08-12）：按客户简称取下一个流水号（兼容1-3位简称）
     * 报价/询价/产品表单统一走这里
     */
    @GetMapping("/code/next-serial")
    public Result<String> nextSerial(@RequestParam String customerShort) {
        return Result.success(productCodeService.nextSerial(customerShort));
    }
}
