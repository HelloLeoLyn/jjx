package com.jjx.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jjx.inventory.dto.query.IqcQuarantineLedgerQueryDTO;
import com.jjx.inventory.dto.vo.IqcDispositionLedgerRowVO;
import com.jjx.inventory.domain.InventoryIqcDispositionOrder;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InventoryIqcDispositionOrderMapper extends BaseMapper<InventoryIqcDispositionOrder> {
    @Select("""
            <script>
            SELECT d.*, inbound.inbound_no, inbound.source_no,
                   inbound.supplier_id, inbound.supplier_name
            FROM inventory_iqc_disposition_order d
            JOIN inventory_inbound_order inbound ON inbound.inbound_id = d.inbound_id
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
}
