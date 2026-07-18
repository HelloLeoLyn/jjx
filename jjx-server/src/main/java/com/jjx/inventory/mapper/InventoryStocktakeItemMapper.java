package com.jjx.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.inventory.domain.InventoryStocktakeItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 盘点明细Mapper接口
 */
@Mapper
public interface InventoryStocktakeItemMapper extends BaseMapper<InventoryStocktakeItem> {

    /**
     * 根据盘点单ID查询明细列表
     */
    @Select("SELECT * FROM inventory_stocktake_item WHERE stocktake_id = #{stocktakeId}")
    List<InventoryStocktakeItem> selectByStocktakeId(@Param("stocktakeId") Long stocktakeId);

    /**
     * 更新差异处理状态
     */
    @Update("UPDATE inventory_stocktake_item SET adjust_status = #{adjustStatus}, " +
            "adjust_order_id = #{adjustOrderId}, reason = #{reason} " +
            "WHERE item_id = #{itemId}")
    int updateAdjustStatus(@Param("itemId") Long itemId,
                           @Param("adjustStatus") String adjustStatus,
                           @Param("adjustOrderId") Long adjustOrderId,
                           @Param("reason") String reason);

}
