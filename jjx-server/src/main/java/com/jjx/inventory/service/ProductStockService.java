package com.jjx.inventory.service;

import com.jjx.inventory.domain.ProductStock;

import java.math.BigDecimal;
import java.util.List;

/**
 * 产品库存服务（产品维度独立记账，与物料库存各自独立）
 * 概念红线：完工入库=产品入库（产品库存+），销售出库=产品出库（产品库存-），
 * 产品看库存直接查本表；产品≠物料
 */
public interface ProductStockService {

    /**
     * 产品入库：产品库存+（无记录则初始化，幂等累加）
     */
    void increase(Long productId, String productCode, String productName, BigDecimal qty);

    /**
     * 产品出库：产品库存-（可用量不足抛异常）
     */
    void decrease(Long productId, BigDecimal qty);

    /**
     * 按产品ID查产品库存
     */
    ProductStock getByProductId(Long productId);

    /**
     * 查询所有产品库存（产品维度看库存）
     */
    List<ProductStock> listAll();
}
