package com.jjx.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.product.domain.entity.EngineeringBomItem;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 产品BOM明细Mapper接口
 */
@Mapper
public interface EngineeringBomItemMapper extends BaseMapper<EngineeringBomItem> {

    @Delete("delete from engineering_bom_item where bom_id=#{bomId}")
    void deleteByBomId(@Param("bomId") Long bomId);

}
