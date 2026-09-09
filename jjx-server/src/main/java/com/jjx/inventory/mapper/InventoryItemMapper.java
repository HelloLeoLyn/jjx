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
}
