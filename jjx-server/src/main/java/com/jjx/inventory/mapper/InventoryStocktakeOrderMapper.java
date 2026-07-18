package com.jjx.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.inventory.domain.InventoryStocktakeOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 盘点单Mapper接口
 */
@Mapper
public interface InventoryStocktakeOrderMapper extends BaseMapper<InventoryStocktakeOrder> {

    /**
     * 查询进行中的盘点单
     */
    @Select("SELECT * FROM inventory_stocktake_order WHERE order_status = 'processing'")
    List<InventoryStocktakeOrder> selectProcessing();

    /**
     * 查询待审批的盘点单
     */
    @Select("SELECT * FROM inventory_stocktake_order WHERE approve_status = 'pending' AND order_status = 'closed'")
    List<InventoryStocktakeOrder> selectPendingApproval();

    /**
     * 更新盘点单状态
     */
    @Update("UPDATE inventory_stocktake_order SET order_status = #{status}, update_time = NOW() " +
            "WHERE stocktake_id = #{stocktakeId}")
    int updateStatus(@Param("stocktakeId") Long stocktakeId, @Param("status") String status);

}
