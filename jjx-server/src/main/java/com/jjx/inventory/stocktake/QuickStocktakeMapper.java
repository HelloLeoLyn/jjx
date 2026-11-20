package com.jjx.inventory.stocktake;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jjx.inventory.domain.InventoryStocktakeOrder;
import org.apache.ibatis.annotations.*;
@Mapper
public interface QuickStocktakeMapper {
    @Update("UPDATE inventory_stocktake_order SET total_diff_amount=NULL WHERE stocktake_id=#{id}")
    int markUnknownCost(@Param("id") Long id);
    @Select("SELECT warehouse_id FROM inventory_warehouse WHERE warehouse_id=#{id} FOR UPDATE")
    Long lockWarehouse(@Param("id") Long id);
    @Select("SELECT * FROM inventory_stocktake_order WHERE stocktake_id=#{id} FOR UPDATE")
    InventoryStocktakeOrder lockOrder(@Param("id") Long id);
    @Select("SELECT * FROM inventory_stocktake_order WHERE stocktake_no=#{prefix} FOR UPDATE")
    InventoryStocktakeOrder findRequest(@Param("prefix") String prefix);
    @Select("SELECT o.* FROM inventory_stocktake_order o JOIN inventory_stocktake_item i ON i.stocktake_id=o.stocktake_id "
        +"WHERE i.inventory_item_id=#{inventoryItemId} AND o.remark LIKE 'QST:%' ORDER BY o.stocktake_id DESC")
    IPage<InventoryStocktakeOrder> records(Page<InventoryStocktakeOrder> page,@Param("inventoryItemId") Long inventoryItemId);
}
