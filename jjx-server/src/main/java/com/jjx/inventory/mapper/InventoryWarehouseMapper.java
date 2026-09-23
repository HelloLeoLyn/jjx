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
     *
     * <p>2026-09-23（dev-20260923-007）修正 status 语义：本模块（前端开关 `active-value="1"`、表单单选「1 正常 / 0 停用」、
     * inventory_warehouse 存量数据）的启用值是 <b>1</b>，原按 RuoYi 老惯例写 `status = '0'` 永远查不到任何仓库
     * → 采购入库选仓静默落兑底仓（成品仓）。此处与前端/存量数据对齐。
     */
    @Select("SELECT * FROM inventory_warehouse WHERE status = '1' ORDER BY sort_order")
    List<InventoryWarehouse> selectAllEnabled();

    /**
     * 根据仓库类型查询（status 语义同 selectAllEnabled）
     */
    @Select("SELECT * FROM inventory_warehouse WHERE warehouse_type = #{warehouseType} AND status = '1'")
    List<InventoryWarehouse> selectByType(@Param("warehouseType") String warehouseType);

    /**
     * 检查仓库编码是否存在
     */
    @Select("SELECT COUNT(*) FROM inventory_warehouse WHERE warehouse_code = #{warehouseCode}")
    int countByCode(@Param("warehouseCode") String warehouseCode);

}
