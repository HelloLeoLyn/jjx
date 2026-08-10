package com.jjx.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.inventory.domain.ProductStock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 产品库存汇总表 Mapper（产品维度独立记账）
 */
@Mapper
public interface ProductStockMapper extends BaseMapper<ProductStock> {

    /**
     * 根据产品ID查询产品库存记录
     */
    @Select("SELECT * FROM product_stock WHERE product_id = #{productId}")
    ProductStock selectByProductId(@Param("productId") Long productId);

    /**
     * 产品库存增加（不存在则先插入 0 记录，再累加）
     */
    @Update("INSERT INTO product_stock (product_id, product_code, product_name, total_quantity, total_reserved) " +
            "VALUES (#{productId}, #{productCode}, #{productName}, 0, 0) " +
            "ON DUPLICATE KEY UPDATE total_quantity = total_quantity + #{qty}")
    int increaseStock(@Param("productId") Long productId,
                      @Param("productCode") String productCode,
                      @Param("productName") String productName,
                      @Param("qty") java.math.BigDecimal qty);

    /**
     * 产品库存扣减（扣减可用量：total_quantity - total_reserved 必须足够）
     * 返回受影响行数；0 = 产品无库存记录或可用不足
     */
    @Update("UPDATE product_stock SET total_quantity = total_quantity - #{qty} " +
            "WHERE product_id = #{productId} " +
            "AND (total_quantity - total_reserved) >= #{qty}")
    int decreaseStock(@Param("productId") Long productId,
                      @Param("qty") java.math.BigDecimal qty);
}
