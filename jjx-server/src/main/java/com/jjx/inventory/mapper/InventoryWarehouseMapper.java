package com.jjx.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.inventory.domain.InventoryWarehouse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 仓库Mapper接口
 */
@Mapper
public interface InventoryWarehouseMapper extends BaseMapper<InventoryWarehouse> {

    /**
     * 查询所有启用的仓库
     */
    @Select("SELECT * FROM inventory_warehouse WHERE status = '0' ORDER BY sort_order")
    List<InventoryWarehouse> selectAllEnabled();

    /**
     * 根据仓库类型查询
     */
    @Select("SELECT * FROM inventory_warehouse WHERE warehouse_type = #{warehouseType} AND status = '0'")
    List<InventoryWarehouse> selectByType(@Param("warehouseType") String warehouseType);

    /**
     * 检查仓库编码是否存在
     */
    @Select("SELECT COUNT(*) FROM inventory_warehouse WHERE warehouse_code = #{warehouseCode}")
    int countByCode(@Param("warehouseCode") String warehouseCode);

}
