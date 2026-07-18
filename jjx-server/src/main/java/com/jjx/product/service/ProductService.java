package com.jjx.product.service;

import com.jjx.common.exception.BusinessException;
import com.jjx.common.exception.BusinessExceptionEnum;
import com.jjx.product.domain.dto.ProductConfigDTO;
import com.jjx.product.domain.dto.ProductConfigRouteDTO;
import com.jjx.product.domain.entity.Product;
import com.jjx.product.domain.vo.ProductConfigResult;
import com.jjx.product.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProductService {
    private final ProductMapper productMapper;

    public ProductConfigResult setBom(ProductConfigDTO dto) {
        Product product = productMapper.selectById(dto.getProductId());
        if (product == null) {
            throw new BusinessException(BusinessExceptionEnum.PRODUCT_NOT_FOUND);
        }
        product.setCurrentBomId(dto.getCurrentBomId());
        productMapper.updateById(product);

        ProductConfigResult result = new ProductConfigResult();
        result.setProductId(dto.getProductId());
        result.setBomId(dto.getCurrentBomId());
        result.setSuccess(true);
        return result;
    }

    public ProductConfigResult setRoute(ProductConfigRouteDTO dto) {
        Product product = productMapper.selectById(dto.getProductId());
        if (product == null) {
            throw new BusinessException(BusinessExceptionEnum.PRODUCT_NOT_FOUND);
        }
        product.setCurrentRouteId(dto.getCurrentRouteId());
        productMapper.updateById(product);

        ProductConfigResult result = new ProductConfigResult();
        result.setProductId(dto.getProductId());
        result.setRouteId(dto.getCurrentRouteId());
        result.setSuccess(true);
        return result;
    }
}
