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
    @Select("SELECT DISTINCT d.* FROM sales_delivery d LEFT JOIN sales_delivery_item i ON i.delivery_id=d.delivery_id " +
        "LEFT JOIN sales_order_product p ON p.id=i.order_product_id WHERE d.deleted=0 " +
        "AND (d.order_id=#{orderId} OR p.order_id=#{orderId}) ORDER BY d.create_time DESC")
    java.util.List<SalesDelivery> selectByOrder(@Param("orderId") Long orderId);
    /** 包括历史无明细单，避免遗漏占用后允许重复安排。 */
    @org.apache.ibatis.annotations.Select("""
        <script>
        SELECT DISTINCT d.* FROM sales_delivery d
        LEFT JOIN sales_delivery_item i ON i.delivery_id=d.delivery_id
        LEFT JOIN sales_order_product p ON p.id=i.order_product_id
        WHERE d.deleted=0 AND d.delivery_status IN
        <foreach collection="statuses" item="status" open="(" separator="," close=")">#{status}</foreach>
        AND (d.order_id IN
        <foreach collection="orderIds" item="orderId" open="(" separator="," close=")">#{orderId}</foreach>
        OR p.order_id IN
        <foreach collection="orderIds" item="orderId" open="(" separator="," close=")">#{orderId}</foreach>)
        </script>
        """)
    java.util.List<SalesDelivery> selectActiveBySourceOrders(
        @org.apache.ibatis.annotations.Param("orderIds") java.util.Collection<Long> orderIds,
        @org.apache.ibatis.annotations.Param("statuses") java.util.Collection<Integer> statuses);
}
