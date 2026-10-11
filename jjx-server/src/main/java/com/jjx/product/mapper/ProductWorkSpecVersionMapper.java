package com.jjx.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.product.domain.entity.ProductWorkSpecVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 产品作业规范发布版本 Mapper（dev-20261011-008） */
@Mapper
public interface ProductWorkSpecVersionMapper extends BaseMapper<ProductWorkSpecVersion> {

    /** 某产品的全部发布版本（新→旧） */
    @Select("SELECT * FROM product_work_spec_version WHERE product_id = #{productId} AND deleted = 0 "
            + "ORDER BY id DESC")
    List<ProductWorkSpecVersion> selectByProductId(@Param("productId") Long productId);
}
