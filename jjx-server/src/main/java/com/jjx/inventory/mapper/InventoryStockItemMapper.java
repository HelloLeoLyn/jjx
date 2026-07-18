package com.jjx.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.inventory.domain.InventoryStockItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

/**
 * 库存批次明细表 Mapper
 */
@Mapper
public interface InventoryStockItemMapper extends BaseMapper<InventoryStockItem> {

    /**
     * 根据物料ID查询所有生效的明细
     */
    @Select("SELECT * FROM inventory_stock_item WHERE material_id = #{materialId} AND status = 1 ORDER BY expiry_date ASC, last_inbound_time ASC")
    List<InventoryStockItem> selectActiveByMaterialId(@Param("materialId") Long materialId);

    /**
     * 根据物料ID和仓库ID查询生效的明细
     */
    @Select("SELECT * FROM inventory_stock_item WHERE material_id = #{materialId} AND warehouse_id = #{warehouseId} AND status = 1 ORDER BY expiry_date ASC, last_inbound_time ASC")
    List<InventoryStockItem> selectActiveByMaterialAndWarehouse(@Param("materialId") Long materialId, @Param("warehouseId") Long warehouseId);

    /**
     * 按FIFO顺序获取可用的批次明细（用于出库扣减）
     */
    @Select("SELECT * FROM inventory_stock_item WHERE material_id = #{materialId} AND status = 1 AND quantity - reserved_quantity > 0 ORDER BY expiry_date ASC, last_inbound_time ASC")
    List<InventoryStockItem> selectFIFOAvailable(@Param("materialId") Long materialId);

    /**
     * 获取指定物料的总数量
     */
    @Select("SELECT COALESCE(SUM(quantity), 0) FROM inventory_stock_item WHERE material_id = #{materialId} AND status = 1")
    BigDecimal sumQuantityByMaterialId(@Param("materialId") Long materialId);

    /**
     * 获取指定物料的总预留数量
     */
    @Select("SELECT COALESCE(SUM(reserved_quantity), 0) FROM inventory_stock_item WHERE material_id = #{materialId} AND status = 1")
    BigDecimal sumReservedByMaterialId(@Param("materialId") Long materialId);

    /**
     * 获取指定物料的最早有效期
     */
    @Select("SELECT MIN(expiry_date) FROM inventory_stock_item WHERE material_id = #{materialId} AND status = 1 AND quantity > 0 AND expiry_date IS NOT NULL")
    java.time.LocalDate selectEarliestExpiry(@Param("materialId") Long materialId);

    /**
     * 获取指定物料最早批次所在的库位ID
     */
    @Select("SELECT location_id FROM inventory_stock_item WHERE material_id = #{materialId} AND status = 1 AND quantity > 0 ORDER BY expiry_date ASC, last_inbound_time ASC LIMIT 1")
    Long selectEarliestLocationId(@Param("materialId") Long materialId);

    /**
     * 扣减指定批次的库存
     */
    @Update("UPDATE inventory_stock_item SET quantity = quantity - #{quantity}, last_outbound_time = NOW() WHERE item_id = #{itemId} AND quantity >= #{quantity}")
    int deductStock(@Param("itemId") Long itemId, @Param("quantity") BigDecimal quantity);

    /**
     * 增加指定批次的预留数量
     */
    @Update("UPDATE inventory_stock_item SET reserved_quantity = reserved_quantity + #{quantity} WHERE item_id = #{itemId}")
    int addReserved(@Param("itemId") Long itemId, @Param("quantity") BigDecimal quantity);

    /**
     * 释放指定批次的预留数量
     */
    @Update("UPDATE inventory_stock_item SET reserved_quantity = reserved_quantity - #{quantity} WHERE item_id = #{itemId} AND reserved_quantity >= #{quantity}")
    int releaseReserved(@Param("itemId") Long itemId, @Param("quantity") BigDecimal quantity);
}
