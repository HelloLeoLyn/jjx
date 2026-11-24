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

    // 与 currentIqcContext 一致：复检不覆盖原入库明细的 lot_id。
    String CURRENT_IQC_LOT = "COALESCE((SELECT d.lot_id FROM inventory_iqc_disposition_order d " +
            "WHERE d.inbound_item_id=i.item_id AND d.action='REWORK' AND d.lot_id IS NOT NULL " +
            "AND d.status IN ('PENDING_REINSPECTION','COMPLETED') ORDER BY d.disposition_id DESC LIMIT 1), i.lot_id)";
    String CURRENT_IQC_ROWS = " FROM inventory_inbound_item i LEFT JOIN quality_lot q ON q.lot_id=" +
            CURRENT_IQC_LOT + " WHERE i.inbound_id=o.inbound_id ";

    /**
     * IQC 工作台分页（dev-20261124-001）：统计列已由写时刷新落到 inventory_inbound_order，
     * 这里只做单表分页，不再对每行执行 10 个相关子查询。
     */
    @Select("<script>" +
            "SELECT o.inbound_id, o.inbound_no, o.source_no, o.supplier_name, o.total_quantity, " +
            "o.iqc_material_count AS materialCount, " +
            "o.iqc_item_count AS itemCount, " +
            "o.iqc_inspected_count AS inspectedCount, " +
            "o.iqc_pending_review_count AS pendingReviewCount, " +
            "o.iqc_pending_inspection_count AS pendingInspectionCount, " +
            "o.iqc_pending_reinspection_count AS pendingReinspectionCount, " +
            "o.iqc_remaining_disposition_qty AS remainingDispositionQuantity, " +
            "o.iqc_pending_scrap_count AS pendingScrapCount, " +
            "o.iqc_pending_rework_count AS pendingReworkCount, " +
            "o.iqc_approved_count AS approvedCount, " +
            "o.iqc_fail_row_count AS failRowCount, " +
            "o.order_status, o.inspection_result, o.create_time " +
            "FROM inventory_inbound_order o " +
            "WHERE o.source_type = #{sourceType} " +
            "<choose><when test='orderStatus != null'>AND o.order_status = #{orderStatus} </when>" +
            "<otherwise>AND o.order_status IN (#{pendingStatus}, #{approvedStatus}, #{completedStatus}) </otherwise></choose>" +
            "<if test='fillInspection != null and fillInspection'>AND o.inspection_result IS NOT NULL AND o.inspection_result NOT IN ('', 'PENDING') </if>" +
            "<if test='fillInspection != null and !fillInspection'>AND (o.inspection_result IS NULL OR o.inspection_result = '' OR o.inspection_result = 'PENDING') </if>" +
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
     * 刷新单张入库单的 IQC 统计（dev-20261124-001）。
     * 只更新 1 行，成本与表总量无关；业务写操作完成后在同一事务内调用。
     * 口径与原 selectIqcPendingPage 的 10 个相关子查询完全一致（见迁移 384 的注释）。
     */
    @Update("UPDATE inventory_inbound_order o SET " +
            "o.iqc_material_count = (SELECT COUNT(DISTINCT i.material_id) FROM inventory_inbound_item i WHERE i.inbound_id = o.inbound_id AND i.material_id IS NOT NULL), " +
            "o.iqc_item_count = (SELECT COUNT(*) FROM inventory_inbound_item i WHERE i.inbound_id = o.inbound_id), " +
            "o.iqc_inspected_count = (SELECT COUNT(DISTINCT i.material_id) FROM inventory_inbound_item i WHERE i.inbound_id = o.inbound_id AND i.material_id IS NOT NULL AND i.lot_id IS NOT NULL), " +
            "o.iqc_pending_review_count = (SELECT COUNT(*)" + CURRENT_IQC_ROWS + "AND q.review_status='PENDING'), " +
            "o.iqc_pending_inspection_count = (SELECT COUNT(*)" + CURRENT_IQC_ROWS + "AND (q.review_status IS NULL OR q.review_status IN ('DRAFT','REJECTED')) AND q.parent_lot_id IS NULL), " +
            "o.iqc_pending_reinspection_count = (SELECT COUNT(*)" + CURRENT_IQC_ROWS + "AND (q.review_status IS NULL OR q.review_status IN ('DRAFT','REJECTED')) AND q.parent_lot_id IS NOT NULL), " +
            "o.iqc_remaining_disposition_qty = (SELECT COALESCE(SUM(GREATEST(q.remaining_quantity, 0)),0) FROM inventory_iqc_quarantine q WHERE q.inbound_id=o.inbound_id), " +
            "o.iqc_pending_scrap_count = (SELECT COUNT(*) FROM inventory_iqc_disposition_order d WHERE d.inbound_id=o.inbound_id AND d.action='SCRAP' AND d.status='PENDING_APPROVAL'), " +
            "o.iqc_pending_rework_count = (SELECT COUNT(*) FROM inventory_iqc_disposition_order d WHERE d.inbound_id=o.inbound_id AND d.action='REWORK' AND d.status='CREATED'), " +
            "o.iqc_approved_count = (SELECT COUNT(*) FROM inventory_inbound_item i JOIN quality_lot q ON q.lot_id = i.lot_id WHERE i.inbound_id = o.inbound_id AND q.review_status = 'APPROVED'), " +
            "o.iqc_fail_row_count = (SELECT COUNT(*) FROM inventory_inbound_item i WHERE i.inbound_id = o.inbound_id AND i.inspection_result = 'FAIL'), " +
            "o.iqc_stats_time = NOW() " +
            "WHERE o.inbound_id = #{inboundId}")
    int refreshIqcStats(@Param("inboundId") Long inboundId);

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

}
