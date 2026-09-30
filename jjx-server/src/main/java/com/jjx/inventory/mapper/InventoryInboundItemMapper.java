package com.jjx.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.inventory.domain.InventoryInboundItem;
import com.jjx.inventory.dto.vo.InboundLotSummaryVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 入库单明细Mapper接口
 */
@Mapper
public interface InventoryInboundItemMapper extends BaseMapper<InventoryInboundItem> {

    /**
     * 根据入库单ID查询明细列表
     */
    @Select("SELECT * FROM inventory_inbound_item WHERE inbound_id = #{inboundId} ORDER BY sort_order")
    List<InventoryInboundItem> selectByInboundId(@Param("inboundId") Long inboundId);

    /**
     * 只读定位：由明细ID取所属入库单ID。
     * 用于「先锁单、再锁明细」的加锁顺序（该列一经写入不再变化，不依赖读到的版本新旧）。
     */
    @Select("SELECT inbound_id FROM inventory_inbound_item WHERE item_id = #{itemId}")
    Long selectInboundIdByItemId(@Param("itemId") Long itemId);

    /**
     * 当前读：锁住入库明细行（2026-09-21 dev-20260921-003）。
     * 复核判定必须拿最新已提交状态，普通一致性读会读到事务开始时的旧快照。
     */
    @Select("SELECT * FROM inventory_inbound_item WHERE item_id = #{itemId} FOR UPDATE")
    InventoryInboundItem selectByIdForUpdate(@Param("itemId") Long itemId);

    /**
     * 当前读：锁住整单明细，供「全部明细已审核」判定使用（2026-09-21 dev-20260921-003）。
     */
    @Select("SELECT * FROM inventory_inbound_item WHERE inbound_id = #{inboundId} ORDER BY sort_order FOR UPDATE")
    List<InventoryInboundItem> selectByInboundIdForUpdate(@Param("inboundId") Long inboundId);

    /**
     * 批量插入入库单明细
     */
    int batchInsert(@Param("list") List<InventoryInboundItem> list);

    /** LEFT JOIN 保留缺少批次关联的行，汇总时不能把不完整来源显示为完整总数。 */
    @Select("""
        <script>
        SELECT DISTINCT i.inbound_id, l.lot_id, l.lot_no, l.lot_quantity,
               l.pass_quantity AS qualified_quantity, l.fail_quantity AS rejected_quantity
        FROM inventory_inbound_item i
        LEFT JOIN quality_lot l ON l.lot_id=i.lot_id AND l.del_flag=0 AND l.lot_type=#{lotType}
        WHERE i.inbound_id IN
        <foreach collection="inboundIds" item="id" open="(" separator="," close=")">#{id}</foreach>
        ORDER BY i.inbound_id, l.lot_id
        </script>
        """)
    List<InboundLotSummaryVO> selectSourceLots(@Param("inboundIds") List<Long> inboundIds,
                                              @Param("lotType") String lotType);

    /** 按批次展示各张原始入库单及实际过账量，红冲保留负数，作废保留状态。 */
    @Select("""
        <script>
        SELECT i.lot_id, o.inbound_id, o.inbound_no, o.inbound_type, o.order_status AS status,
               SUM(i.quantity) AS quantity, SUM(i.posted_quantity) AS posted_quantity
        FROM inventory_inbound_item i
        JOIN inventory_inbound_order o ON o.inbound_id=i.inbound_id
        WHERE i.lot_id IN
        <foreach collection="lotIds" item="id" open="(" separator="," close=")">#{id}</foreach>
        GROUP BY i.lot_id, o.inbound_id, o.inbound_no, o.inbound_type, o.order_status
        ORDER BY o.inbound_id
        </script>
        """)
    List<InboundLotSummaryVO.InboundDocument> selectLotInboundDocuments(@Param("lotIds") List<Long> lotIds);

    /** 保留报废单状态，前端区分生效与作废，不能只看报废申请量。 */
    @Select("""
        <script>
        SELECT lot_id, scrap_no, quantity, status FROM quality_scrap_order
        WHERE del_flag=0 AND lot_id IN
        <foreach collection="lotIds" item="id" open="(" separator="," close=")">#{id}</foreach>
        ORDER BY scrap_id
        </script>
        """)
    List<InboundLotSummaryVO.ScrapDocument> selectLotScrapDocuments(@Param("lotIds") List<Long> lotIds);

}
