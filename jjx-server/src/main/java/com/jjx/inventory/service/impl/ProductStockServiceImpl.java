package com.jjx.inventory.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.inventory.domain.ProductStock;
import com.jjx.inventory.mapper.ProductStockMapper;
import com.jjx.inventory.service.ProductStockService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 产品库存服务实现（产品维度独立记账）
 */
@Slf4j
@Service
public class ProductStockServiceImpl implements ProductStockService {

    private final ProductStockMapper productStockMapper;

    public ProductStockServiceImpl(ProductStockMapper productStockMapper) {
        this.productStockMapper = productStockMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void increase(Long productId, String productCode, String productName, BigDecimal qty) {
        if (productId == null || qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("产品入库参数不合法: productId={}, qty={}", productId, qty);
            return;
        }
        int rows = productStockMapper.increaseStock(productId, productCode, productName, qty);
        log.info("产品入库: productId={}, qty={}, rows={}", productId, qty, rows);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void decrease(Long productId, BigDecimal qty) {
        if (productId == null || qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("产品出库参数不合法: productId={}, qty={}", productId, qty);
            return;
        }
        int rows = productStockMapper.decreaseStock(productId, qty);
        if (rows == 0) {
            throw new BusinessException("产品[" + productId + "]库存不足，无法出库");
        }
        log.info("产品出库: productId={}, qty={}", productId, qty);
    }

    @Override
    public ProductStock getByProductId(Long productId) {
        return productStockMapper.selectByProductId(productId);
    }

    @Override
    public List<ProductStock> listAll() {
        return productStockMapper.selectList(new LambdaQueryWrapper<ProductStock>()
                .orderByDesc(ProductStock::getLastUpdateTime));
    }
}
