package com.jjx.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.inventory.domain.InventoryInboundOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.util.List;

/**
 * 入库单Mapper接口
 */
@Mapper
public interface InventoryInboundOrderMapper extends BaseMapper<InventoryInboundOrder> {

    /**
     * 根据来源单据查询入库单
     */
    @Select("SELECT * FROM inventory_inbound_order WHERE source_type = #{sourceType} AND source_id = #{sourceId}")
    InventoryInboundOrder selectBySource(@Param("sourceType") String sourceType,
                                          @Param("sourceId") Long sourceId);

    /**
     * 查询待审批的入库单
     */
    @Select("SELECT * FROM inventory_inbound_order WHERE approve_status = 'pending' AND order_status = 'draft'")
    List<InventoryInboundOrder> selectPendingApproval();

    /**
     * 查询指定日期范围内的入库单
     */
    @Select("SELECT * FROM inventory_inbound_order WHERE inbound_date BETWEEN #{startDate} AND #{endDate}")
    List<InventoryInboundOrder> selectByDateRange(@Param("startDate") LocalDate startDate,
                                                   @Param("endDate") LocalDate endDate);

    /**
     * 更新入库单状态
     */
    @Update("UPDATE inventory_inbound_order SET order_status = #{status}, update_time = NOW() " +
            "WHERE inbound_id = #{inboundId}")
    int updateStatus(@Param("inboundId") Long inboundId, @Param("status") String status);

    /**
     * 审批入库单
     */
    @Update("UPDATE inventory_inbound_order SET approve_status = #{approveStatus}, " +
            "approver_id = #{approverId}, approver_name = #{approverName}, " +
            "approve_time = NOW(), approve_remark = #{approveRemark} " +
            "WHERE inbound_id = #{inboundId}")
    int approve(@Param("inboundId") Long inboundId,
                @Param("approveStatus") String approveStatus,
                @Param("approverId") Long approverId,
                @Param("approverName") String approverName,
                @Param("approveRemark") String approveRemark);

}
