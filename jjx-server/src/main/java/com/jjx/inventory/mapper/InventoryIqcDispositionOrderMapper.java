package com.jjx.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jjx.inventory.dto.query.IqcQuarantineLedgerQueryDTO;
import com.jjx.inventory.dto.vo.IqcDispositionLedgerRowVO;
import com.jjx.inventory.domain.InventoryIqcDispositionOrder;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Mapper;

import java.math.BigDecimal;

@Mapper
public interface InventoryIqcDispositionOrderMapper extends BaseMapper<InventoryIqcDispositionOrder> {
    @Select("""
            <script>
            SELECT d.*, inbound.inbound_no, inbound.source_no,
                   inbound.supplier_id, inbound.supplier_name, lot.lot_no
            FROM inventory_iqc_disposition_order d
            JOIN inventory_inbound_order inbound ON inbound.inbound_id = d.inbound_id
            LEFT JOIN quality_lot lot ON lot.lot_id = d.lot_id
            <where>
                <if test="query.materialKeyword != null and query.materialKeyword != ''">
                    AND (d.material_code LIKE CONCAT('%', #{query.materialKeyword}, '%')
                         OR d.material_name LIKE CONCAT('%', #{query.materialKeyword}, '%'))
                </if>
                <if test="query.batchNo != null and query.batchNo != ''">
                    AND d.batch_no LIKE CONCAT('%', #{query.batchNo}, '%')
                </if>
                <if test="query.inboundNo != null and query.inboundNo != ''">
                    AND inbound.inbound_no LIKE CONCAT('%', #{query.inboundNo}, '%')
                </if>
                <if test="query.sourceNo != null and query.sourceNo != ''">
                    AND inbound.source_no LIKE CONCAT('%', #{query.sourceNo}, '%')
                </if>
                <if test="query.supplierName != null and query.supplierName != ''">
                    AND inbound.supplier_name LIKE CONCAT('%', #{query.supplierName}, '%')
                </if>
            </where>
            ORDER BY d.disposition_id DESC
            </script>
            """)
    Page<IqcDispositionLedgerRowVO> selectLedgerPage(
            Page<IqcDispositionLedgerRowVO> page,
            @Param("query") IqcQuarantineLedgerQueryDTO query);

    /**
     * 未终结处置单数（dev-20260929-004）—— 返工待完成 / 待复检、报废待审批、让步待确认入库。
     * 存在即在途，检验批不得关闭。
     */
    @Select("SELECT COUNT(*) FROM inventory_iqc_disposition_order WHERE lot_id = #{lotId} "
            + "AND status IN ('CREATED', 'PENDING_REINSPECTION', 'PENDING_APPROVAL', 'PENDING_INBOUND')")
    long countInFlightByLotId(@Param("lotId") Long lotId);

    /**
     * 该批「已确认入库的让步放行量」（dev-20260929-004）—— 用于把让步件从"合格入库"桶里扣出，
     * 避免 stored ≥ pass 被让步件凑满（IN260929005 lot1 的关批偶然性）。
     */
    @Select("SELECT COALESCE(SUM(quantity), 0) FROM inventory_iqc_disposition_order "
            + "WHERE lot_id = #{lotId} AND action = 'RELEASE' AND status = 'COMPLETED'")
    BigDecimal sumConfirmedReleaseQuantityByLotId(@Param("lotId") Long lotId);
}
