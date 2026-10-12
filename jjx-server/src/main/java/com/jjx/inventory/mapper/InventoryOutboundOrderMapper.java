package com.jjx.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.inventory.domain.InventoryOutboundOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.util.List;

/**
 * 出库单Mapper接口
 */
@Mapper
public interface InventoryOutboundOrderMapper extends BaseMapper<InventoryOutboundOrder> {

    /**
     * 根据来源单据查询出库单
     */
    @Select("SELECT * FROM inventory_outbound_order WHERE source_type = #{sourceType} AND source_id = #{sourceId}")
    InventoryOutboundOrder selectBySource(@Param("sourceType") String sourceType,
                                           @Param("sourceId") Long sourceId);

    /**
     * 行锁查询出库单（DEV-651 方案A：并发锁单，杜绝重复出入库）
     * 必须在事务内调用，锁住单据行直到事务提交/回滚
     */
    @Select("SELECT * FROM inventory_outbound_order WHERE outbound_id = #{outboundId} FOR UPDATE")
    InventoryOutboundOrder selectByIdForUpdate(@Param("outboundId") Long outboundId);

    /**
     * 查询待审批的出库单
     */
    @Select("SELECT * FROM inventory_outbound_order WHERE approve_status = 'pending' AND order_status = 'draft'")
    List<InventoryOutboundOrder> selectPendingApproval();

    /**
     * 查询指定日期范围内的出库单
     */
    @Select("SELECT * FROM inventory_outbound_order WHERE outbound_date BETWEEN #{startDate} AND #{endDate}")
    List<InventoryOutboundOrder> selectByDateRange(@Param("startDate") LocalDate startDate,
                                                    @Param("endDate") LocalDate endDate);

    /**
     * 更新出库单状态
     */
    @Update("UPDATE inventory_outbound_order SET order_status = #{status}, update_time = NOW() " +
            "WHERE outbound_id = #{outboundId}")
    int updateStatus(@Param("outboundId") Long outboundId, @Param("status") String status);

    /**
     * æäº§åå³èçé¢æåºåºåï¼ææ ·é¢æ source_type='sample' + çäº§é¢æ source_type='work_order'ï¼ï¼åªè¯»ã
     */
    @Select("SELECT o.outbound_id AS outboundId, o.outbound_no AS outboundNo, o.outbound_type AS outboundType, " +
            "o.source_type AS sourceType, o.source_no AS sourceNo, o.outbound_date AS outboundDate, " +
            "o.total_quantity AS totalQuantity, o.order_status AS orderStatus, o.create_time AS createTime " +
            "FROM inventory_outbound_order o " +
            "JOIN sales_sample_order ss ON o.source_type='sample' AND ss.sample_order_id=o.source_id AND ss.deleted=0 " +
            "WHERE ss.product_id = #{productId} " +
            "UNION ALL " +
            "SELECT o.outbound_id, o.outbound_no, o.outbound_type, o.source_type, o.source_no, o.outbound_date, " +
            "o.total_quantity, o.order_status, o.create_time " +
            "FROM inventory_outbound_order o " +
            "JOIN production_order po ON o.source_type='work_order' AND po.order_id=o.source_id " +
            "WHERE po.product_id = #{productId} " +
            "ORDER BY createTime DESC")
    List<java.util.Map<String, Object>> selectPickByProduct(@Param("productId") Long productId);

}
