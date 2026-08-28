package com.jjx.product.service;

import com.jjx.common.exception.BusinessException;
import com.jjx.product.domain.entity.Product;
import com.jjx.product.enums.ProductEnums;
import com.jjx.product.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** 客户定制产品的归属校验，防止绕过前端提交其他客户的产品。 */
@Component
@RequiredArgsConstructor
public class ProductCustomerValidator {
    private final ProductMapper productMapper;

    public Product validateBelongsToCustomer(Long productId, Long customerId) {
        if (productId == null) throw new BusinessException("产品不能为空");
        if (customerId == null) throw new BusinessException("客户不能为空");
        Product product = productMapper.selectById(productId);
        if (product == null) throw new BusinessException("产品不存在");
        if (!Objects.equals(product.getCustomerId(), customerId)) {
            throw new BusinessException("产品[" + product.getProductName() + "]不属于当前客户");
        }
        return product;
    }

    public Product validateSelectable(Long productId, Long customerId) {
        Product product = validateBelongsToCustomer(productId, customerId);
        if (!ProductEnums.Status.RELEASED.getValue().equals(product.getProductStatus())) {
            throw new BusinessException("产品[" + product.getProductName() + "]尚未发布，不能选择");
        }
        return product;
    }
}
