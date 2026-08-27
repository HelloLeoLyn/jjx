package com.jjx.sales.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.sales.domain.entity.SalesSampleBom;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 打样BOM物料清单Mapper
 */
@Mapper
public interface SalesSampleBomMapper extends BaseMapper<SalesSampleBom> {

    /**
     * 查询样品单所有打样BOM（按层结构排序）
     */
    @Select("SELECT * FROM sales_sample_bom WHERE order_id = #{orderId} ORDER BY bom_id ASC")
    List<SalesSampleBom> selectByOrderId(@Param("orderId") Long orderId);
}
