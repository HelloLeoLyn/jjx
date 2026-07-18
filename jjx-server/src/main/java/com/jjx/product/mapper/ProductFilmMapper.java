package com.jjx.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.product.domain.entity.ProductFilm;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ProductFilmMapper extends BaseMapper<ProductFilm> {
    
    /**
     * 根据产品ID获取菲林列表
     */
    @Select("SELECT * FROM product_film WHERE product_id = #{productId} AND deleted = 0 ORDER BY FIELD(film_type, 'OVERLAY', 'UPPER_CIRCUIT', 'SPACER', 'LOWER_CIRCUIT', 'BACK_ADHESIVE')")
    List<ProductFilm> selectByProductId(@Param("productId") Long productId);
    
    /**
     * 根据产品ID获取当前版本菲林
     */
    @Select("SELECT * FROM product_film WHERE product_id = #{productId} AND is_current = 1 AND deleted = 0")
    List<ProductFilm> selectCurrentByProductId(@Param("productId") Long productId);
    
    /**
     * 根据产品ID和类型获取菲林
     */
    @Select("SELECT * FROM product_film WHERE product_id = #{productId} AND film_type = #{filmType} AND is_current = 1 AND deleted = 0")
    ProductFilm selectByProductIdAndType(@Param("productId") Long productId, @Param("filmType") String filmType);
    
    /**
     * 设置指定产品的所有菲林为非当前版本
     */
    @Update("UPDATE product_film SET is_current = 0 WHERE product_id = #{productId}")
    void setAllNotCurrent(@Param("productId") Long productId);
    
    /**
     * 设置指定产品的同类型菲林为非当前版本
     */
    @Update("UPDATE product_film SET is_current = 0 WHERE product_id = #{productId} AND film_type = #{filmType}")
    void setTypeNotCurrent(@Param("productId") Long productId, @Param("filmType") String filmType);
    
    /**
     * 根据审核状态查询
     */
    @Select("SELECT * FROM product_film WHERE approve_status = #{approveStatus} AND deleted = 0")
    List<ProductFilm> selectByApproveStatus(@Param("approveStatus") Integer approveStatus);
}