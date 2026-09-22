package com.jjx.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jjx.inventory.domain.InventoryIqcQuarantine;
import com.jjx.inventory.dto.query.IqcQuarantineLedgerQueryDTO;
import com.jjx.inventory.dto.vo.IqcQuarantineLedgerRowVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface InventoryIqcQuarantineMapper extends BaseMapper<InventoryIqcQuarantine> {

    @Select("""
            <script>
            SELECT q.*,
                   inbound.inbound_no,
                   inbound.supplier_id,
                   inbound.supplier_name,
                   inbound.source_no,
                   COALESCE(NULLIF(lot.defect_reason, ''), lot.remark) AS defect_reason
            FROM inventory_iqc_quarantine q
            LEFT JOIN inventory_inbound_order inbound ON inbound.inbound_id = q.inbound_id
            LEFT JOIN quality_lot lot ON lot.lot_id = q.lot_id
            <where>
                <if test="query.status != null and query.status != ''">
                    AND q.status = #{query.status}
                </if>
                <if test="query.materialKeyword != null and query.materialKeyword != ''">
                    AND (q.material_code LIKE CONCAT('%', #{query.materialKeyword}, '%')
                         OR q.material_name LIKE CONCAT('%', #{query.materialKeyword}, '%'))
                </if>
                <if test="query.batchNo != null and query.batchNo != ''">
                    AND q.batch_no LIKE CONCAT('%', #{query.batchNo}, '%')
                </if>
                <if test="query.inboundNo != null and query.inboundNo != ''">
                    AND inbound.inbound_no LIKE CONCAT('%', #{query.inboundNo}, '%')
                </if>
                <if test="query.supplierName != null and query.supplierName != ''">
                    AND inbound.supplier_name LIKE CONCAT('%', #{query.supplierName}, '%')
                </if>
            </where>
            ORDER BY q.quarantine_id DESC
            </script>
            """)
    Page<IqcQuarantineLedgerRowVO> selectLedgerPage(
            Page<IqcQuarantineLedgerRowVO> page,
            @Param("query") IqcQuarantineLedgerQueryDTO query);
}
