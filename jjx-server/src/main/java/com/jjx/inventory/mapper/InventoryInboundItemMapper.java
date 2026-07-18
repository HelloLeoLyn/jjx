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
     * 批量插入入库单明细
     */
    int batchInsert(@Param("list") List<InventoryInboundItem> list);

}
