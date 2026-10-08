package com.jjx.sales.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.sales.domain.entity.SalesDelivery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;

/**
 * 销售发货单Mapper接口
 */
@Mapper
public interface SalesDeliveryMapper extends BaseMapper<SalesDelivery> {
    @Select("SELECT * FROM sales_delivery WHERE delivery_id=#{deliveryId} AND deleted=0 FOR UPDATE")
    SalesDelivery selectByIdForUpdate(@Param("deliveryId") Long deliveryId);
}
