package com.jjx.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jjx.inventory.domain.InventoryInboundOrder;
import com.jjx.inventory.dto.vo.IqcPendingVO;
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

    @Select("<script>" +
            "SELECT o.inbound_id, o.inbound_no, o.supplier_name, o.total_quantity, " +
            "(SELECT COUNT(*) FROM inventory_inbound_item i WHERE i.inbound_id = o.inbound_id) AS material_count, " +
            "(SELECT COUNT(*) FROM inventory_inbound_item i WHERE i.inbound_id = o.inbound_id AND i.inspection_id IS NOT NULL) AS inspected_count, " +
            "(SELECT COUNT(*) FROM inventory_inbound_item i JOIN production_quality_inspection q ON q.inspection_id = i.inspection_id WHERE i.inbound_id = o.inbound_id AND q.review_status = 'PENDING') AS pending_review_count, " +
            "(SELECT COUNT(*) FROM inventory_inbound_item i JOIN production_quality_inspection q ON q.inspection_id = i.inspection_id WHERE i.inbound_id = o.inbound_id AND q.review_status = 'APPROVED') AS approved_count, " +
            "(SELECT COUNT(*) FROM inventory_inbound_item i WHERE i.inbound_id = o.inbound_id AND i.inspection_result = 'FAIL') AS fail_row_count, " +
            "o.order_status, o.inspection_result, o.create_time " +
            "FROM inventory_inbound_order o " +
            "WHERE o.source_type = #{sourceType} " +
            "<choose><when test='orderStatus != null'>AND o.order_status = #{orderStatus} </when>" +
            "<otherwise>AND o.order_status IN (#{pendingStatus}, #{approvedStatus}, #{completedStatus}) </otherwise></choose>" +
            "<if test='fillInspection != null and fillInspection'>AND o.inspection_result IS NOT NULL AND o.inspection_result != '' </if>" +
            "<if test='fillInspection != null and !fillInspection'>AND (o.inspection_result IS NULL OR o.inspection_result = '') </if>" +
            "<if test='inboundNo != null and inboundNo != &quot;&quot;'>" +
            "AND o.inbound_no LIKE CONCAT('%', #{inboundNo}, '%') " +
            "</if>" +
            "ORDER BY o.create_time DESC" +
            "</script>")
    IPage<IqcPendingVO> selectIqcPendingPage(Page<IqcPendingVO> page,
                                              @Param("sourceType") String sourceType,
                                              @Param("pendingStatus") Integer pendingStatus,
                                              @Param("approvedStatus") Integer approvedStatus,
                                              @Param("completedStatus") Integer completedStatus,
                                              @Param("orderStatus") Integer orderStatus,
                                              @Param("fillInspection") Boolean fillInspection,
                                              @Param("inboundNo") String inboundNo);

    /**
     * 根据来源单据查询入库单
     */
    @Select("SELECT * FROM inventory_inbound_order WHERE source_type = #{sourceType} AND source_id = #{sourceId}")
    InventoryInboundOrder selectBySource(@Param("sourceType") String sourceType,
                                          @Param("sourceId") Long sourceId);

    /**
     * 行锁查询入库单（DEV-651 方案A：并发锁单，杜绝重复出入库）
     * 必须在事务内调用，锁住单据行直到事务提交/回滚
     */
    @Select("SELECT * FROM inventory_inbound_order WHERE inbound_id = #{inboundId} FOR UPDATE")
    InventoryInboundOrder selectByIdForUpdate(@Param("inboundId") Long inboundId);

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
