package com.jjx.product.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jjx.common.core.page.PageResult;
import com.jjx.common.exception.BusinessException;
import com.jjx.product.domain.dto.ProductPriceUpdateDTO;
import com.jjx.product.domain.entity.Product;
import com.jjx.product.domain.query.ProductQuery;
import com.jjx.product.domain.vo.ProductEditVO;
import com.jjx.product.domain.vo.ProductPriceVO;
import com.jjx.product.domain.vo.ProductVo;
import com.jjx.product.mapper.ProductMapper;
import com.jjx.system.service.OperLogChangeRecorder;
import com.jjx.system.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductPriceService {
    public static final String VIEW_PERMISSION = "product:price:view";
    public static final String EDIT_PERMISSION = "product:price:edit";
    private final ProductMapper productMapper;
    private final OperLogChangeRecorder changeRecorder;

    public PageResult<ProductPriceVO> page(ProductQuery query) {
        StpUtil.checkPermission(VIEW_PERMISSION);
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>()
                .select(Product::getProductId, Product::getProductCode, Product::getProductName,
                        Product::getCustomerName, Product::getUnit, Product::getBasePrice, Product::getCostPrice)
                .like(StringUtils.isNotBlank(query.getProductCode()), Product::getProductCode, query.getProductCode())
                .like(StringUtils.isNotBlank(query.getProductName()), Product::getProductName, query.getProductName())
                .eq(query.getCustomerId() != null, Product::getCustomerId, query.getCustomerId())
                .orderByDesc(Product::getProductId);
        Page<Product> page = productMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        return PageResult.of(page, page.getRecords().stream().map(this::toVO).toList());
    }

    public ProductPriceVO get(Long productId) {
        StpUtil.checkPermission(VIEW_PERMISSION);
        return toVO(requireProduct(productId));
    }

    @Transactional(rollbackFor = Exception.class)
    public ProductEditVO update(Long productId, ProductPriceUpdateDTO dto) {
        StpUtil.checkPermission(VIEW_PERMISSION);
        StpUtil.checkPermission(EDIT_PERMISSION);
        Product old = requireProduct(productId);
        if (!same(old.getBasePrice(), dto.getExpectedBasePrice()) || !same(old.getCostPrice(), dto.getExpectedCostPrice())) {
            throw new BusinessException("价格已被其他人修改，请刷新后重新维护");
        }
        List<String> changes = new ArrayList<>();
        changeRecorder.diffDecimal(changes, "基础售价", old.getBasePrice(), dto.getBasePrice());
        changeRecorder.diffDecimal(changes, "标准成本", old.getCostPrice(), dto.getCostPrice());
        if (!same(old.getBasePrice(), dto.getBasePrice()) || !same(old.getCostPrice(), dto.getCostPrice())) {
            LambdaUpdateWrapper<Product> update = new LambdaUpdateWrapper<Product>()
                    .eq(Product::getProductId, productId)
                    .set(Product::getBasePrice, dto.getBasePrice())
                    .set(Product::getCostPrice, dto.getCostPrice())
                    .set(Product::getUpdateBy, SecurityUtils.getUsername())
                    .set(Product::getUpdateTime, LocalDateTime.now());
            if (old.getBasePrice() == null) update.isNull(Product::getBasePrice);
            else update.eq(Product::getBasePrice, old.getBasePrice());
            if (old.getCostPrice() == null) update.isNull(Product::getCostPrice);
            else update.eq(Product::getCostPrice, old.getCostPrice());
            if (productMapper.update(null, update) != 1) {
                throw new BusinessException("价格已变更或产品已删除，请刷新后重新维护");
            }
        }
        ProductEditVO result = new ProductEditVO();
        result.setSuccess(true);
        result.setDetailMessage(changes.isEmpty() ? "售价与标准成本未变化" : String.join("；", changes));
        return result;
    }

    /** 普通产品查询也遵守价格查看权限，避免只在页面隐藏。 */
    public ProductVo filterPrices(ProductVo product) {
        if (product != null && !canView()) { product.setBasePrice(null); product.setCostPrice(null); }
        return product;
    }

    public Product filterPrices(Product product) {
        if (product != null && !canView()) { product.setBasePrice(null); product.setCostPrice(null); }
        return product;
    }

    private boolean canView() {
        return StpUtil.isLogin() && StpUtil.hasPermission(VIEW_PERMISSION);
    }

    private Product requireProduct(Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) throw new BusinessException("产品不存在");
        return product;
    }

    private ProductPriceVO toVO(Product product) {
        ProductPriceVO result = new ProductPriceVO();
        BeanUtils.copyProperties(product, result);
        return result;
    }

    private static boolean same(BigDecimal first, BigDecimal second) {
        return first == null ? second == null : second != null && first.compareTo(second) == 0;
    }
}
