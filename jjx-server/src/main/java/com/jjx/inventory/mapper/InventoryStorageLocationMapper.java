package com.jjx.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.inventory.domain.InventoryStorageLocation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

/**
 * 库位Mapper接口
 */
@Mapper
public interface InventoryStorageLocationMapper extends BaseMapper<InventoryStorageLocation> {

    /**
     * 查询指定仓库下的所有库位
     */
    @Select("SELECT * FROM inventory_storage_location WHERE warehouse_id = #{warehouseId} AND status = '0' ORDER BY sort_order")
    List<InventoryStorageLocation> selectByWarehouseId(@Param("warehouseId") Long warehouseId);

    /**
     * 查询空库位（未使用或容量充足）
     */
    @Select("SELECT * FROM inventory_storage_location WHERE warehouse_id = #{warehouseId} " +
            "AND (used_capacity IS NULL OR used_capacity < capacity) AND status = '0' " +
            "ORDER BY used_capacity ASC LIMIT #{limit}")
    List<InventoryStorageLocation> selectEmptyLocations(@Param("warehouseId") Long warehouseId,
                                                         @Param("limit") int limit);

    /**
     * 更新库位已使用容量
     */
    @Update("UPDATE inventory_storage_location SET used_capacity = used_capacity + #{quantity} " +
            "WHERE location_id = #{locationId}")
    int updateUsedCapacity(@Param("locationId") Long locationId, @Param("quantity") BigDecimal quantity);

}
