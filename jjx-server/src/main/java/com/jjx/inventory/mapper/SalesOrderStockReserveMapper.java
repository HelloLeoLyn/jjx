package com.jjx.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.inventory.domain.SalesOrderStockReserve;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 销售订单成品库存预留 Mapper
 */
@Mapper
public interface SalesOrderStockReserveMapper extends BaseMapper<SalesOrderStockReserve> {

    /**
     * dev-20260930-019（P2）：按 产品 × 占用类型 汇总该订单的未释放占用。
     *
     * @return 每行：productId / reserveType / qty
     */
    @Select("SELECT product_id AS productId, reserve_type AS reserveType, SUM(reserve_quantity) AS qty " +
            "FROM sales_order_stock_reserve WHERE order_id = #{orderId} AND status = 0 " +
            "GROUP BY product_id, reserve_type")
    java.util.List<java.util.Map<String, Object>> sumActiveByProductAndType(@Param("orderId") Long orderId);
}
