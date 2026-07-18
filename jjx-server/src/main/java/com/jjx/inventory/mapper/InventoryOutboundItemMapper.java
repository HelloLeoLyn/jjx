package com.jjx.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.inventory.domain.InventoryOutboundItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 出库单明细Mapper接口
 */
@Mapper
public interface InventoryOutboundItemMapper extends BaseMapper<InventoryOutboundItem> {

    /**
     * 根据出库单ID查询明细列表
     */
    @Select("SELECT * FROM inventory_outbound_item WHERE outbound_id = #{outboundId} ORDER BY sort_order")
    List<InventoryOutboundItem> selectByOutboundId(@Param("outboundId") Long outboundId);

    /**
     * 批量插入出库单明细
     */
    int batchInsert(@Param("list") List<InventoryOutboundItem> list);

}
