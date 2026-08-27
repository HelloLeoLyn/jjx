package com.jjx.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.inventory.domain.OrderMaterialReserve;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 订单材料预占表 Mapper（094）
 */
@Mapper
public interface OrderMaterialReserveMapper extends BaseMapper<OrderMaterialReserve> {

    /**
     * 查询某订单未释放的预占记录
     */
    @Select("SELECT * FROM order_material_reserve WHERE order_id = #{orderId} AND status = 0")
    List<OrderMaterialReserve> selectActiveByOrder(@Param("orderId") Long orderId);

    /**
     * 按物料汇总所有订单的预占占用（status=0）
     */
    @Select("SELECT material_id, SUM(reserve_quantity) AS total_reserve FROM order_material_reserve " +
            "WHERE status = 0 GROUP BY material_id")
    List<Map<String, Object>> selectGroupByMaterial();

    /**
     * 查询某物料所有订单的预占占用合计（status=0）
     */
    @Select("SELECT COALESCE(SUM(reserve_quantity), 0) FROM order_material_reserve " +
            "WHERE material_id = #{materialId} AND status = 0")
    java.math.BigDecimal selectReserveByMaterial(@Param("materialId") Long materialId);

    /**
     * 查询剩余1天内的预占（快到期提醒）
     */
    @Select("SELECT * FROM order_material_reserve WHERE status = 0 " +
            "AND expire_time BETWEEN NOW() AND DATE_ADD(NOW(), INTERVAL 1 DAY)")
    List<OrderMaterialReserve> selectExpiringSoon();

    /**
     * 查询已过期未释放的预占（超时自动释放）
     */
    @Select("SELECT * FROM order_material_reserve WHERE status = 0 AND expire_time < NOW()")
    List<OrderMaterialReserve> selectExpired();

    /**
     * 释放指定订单指定物料的预占（恢复状态）
     */
    @Update("UPDATE order_material_reserve SET status = 1, release_reason = #{reason}, " +
            "release_by = #{by}, release_time = NOW() " +
            "WHERE order_id = #{orderId} AND status = 0")
    int releaseByOrder(@Param("orderId") Long orderId, @Param("reason") String reason, @Param("by") String by);
}
