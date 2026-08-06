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

}
