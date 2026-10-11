package com.jjx.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.product.domain.entity.ProductWorkSpecItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 产品作业规范发布条目 Mapper（dev-20261011-008） */
@Mapper
public interface ProductWorkSpecItemMapper extends BaseMapper<ProductWorkSpecItem> {

    /** 某发布版本的条目（按展示顺序） */
    @Select("SELECT * FROM product_work_spec_item WHERE spec_version_id = #{specVersionId} "
            + "ORDER BY sort_order ASC, id ASC")
    List<ProductWorkSpecItem> selectByVersionId(@Param("specVersionId") Long specVersionId);
}
