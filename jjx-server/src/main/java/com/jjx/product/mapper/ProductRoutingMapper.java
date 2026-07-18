package com.jjx.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.product.domain.entity.ProductRouting;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ProductRoutingMapper extends BaseMapper<ProductRouting> {
    
    /**
     * 根据产品ID查询当前版本的工艺路线
     */
    @Select("SELECT * FROM product_routing WHERE product_id = #{productId} AND is_current = 1")
    ProductRouting selectCurrentByProductId(@Param("productId") Long productId);
    
    /**
     * 根据产品编码查询当前版本的工艺路线
     */
    @Select("SELECT * FROM product_routing WHERE product_code = #{productCode} AND is_current = 1")
    ProductRouting selectCurrentByProductCode(@Param("productCode") String productCode);
    
    /**
     * 查询产品的所有版本工艺路线
     */
    @Select("SELECT * FROM product_routing WHERE product_id = #{productId} ORDER BY routing_version DESC")
    List<ProductRouting> selectAllVersionsByProductId(@Param("productId") Long productId);
    
    /**
     * 设置指定产品所有路线为非当前版本
     */
    @Update("UPDATE product_routing SET is_current = 0 WHERE product_id = #{productId}")
    void setAllNotCurrent(@Param("productId") Long productId);
    
    /**
     * 根据审核状态查询
     */
    @Select("SELECT * FROM product_routing WHERE approve_status = #{approveStatus}")
    List<ProductRouting> selectByApproveStatus(@Param("approveStatus") Long approveStatus);
}