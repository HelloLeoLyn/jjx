package com.jjx.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.inventory.domain.InventoryInboundItem;
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

}
