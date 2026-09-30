package com.jjx.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.inventory.domain.InventoryItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface InventoryItemMapper extends BaseMapper<InventoryItem> {
    @Select("SELECT * FROM inventory_item WHERE item_type = #{itemType} AND source_id = #{sourceId} LIMIT 1")
    InventoryItem selectBySource(@Param("itemType") String itemType, @Param("sourceId") Long sourceId);

    /** 按编码回查（uk_inventory_item_code 全局唯一，dev-20260930-010 用于识别跨来源重号）。 */
    @Select("SELECT * FROM inventory_item WHERE item_code = #{itemCode} LIMIT 1")
    InventoryItem selectByCode(@Param("itemCode") String itemCode);
}
