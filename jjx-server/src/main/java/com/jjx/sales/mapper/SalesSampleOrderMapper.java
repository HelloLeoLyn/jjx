package com.jjx.sales.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.sales.domain.entity.SalesSampleOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SalesSampleOrderMapper extends BaseMapper<SalesSampleOrder> {
    @Select("SELECT * FROM sales_sample_order WHERE order_id = #{orderId} AND deleted = 0 LIMIT 1")
    SalesSampleOrder selectByOrderId(Long orderId);
}
