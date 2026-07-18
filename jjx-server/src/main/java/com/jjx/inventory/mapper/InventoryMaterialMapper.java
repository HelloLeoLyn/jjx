package com.jjx.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.inventory.domain.InventoryMaterial;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

/**
 * 物料主数据Mapper接口
 */
@Mapper
public interface InventoryMaterialMapper extends BaseMapper<InventoryMaterial> {

    /**
     * 根据物料编码查询
     */
    @Select("SELECT * FROM inventory_material WHERE material_code = #{materialCode}")
    InventoryMaterial selectByCode(@Param("materialCode") String materialCode);

    /**
     * 查询库存预警物料（低于安全库存）
     */
    @Select("SELECT * FROM inventory_material WHERE current_stock < safe_stock AND safe_stock > 0 AND status = 'active'")
    List<InventoryMaterial> selectLowStockMaterials();

    /**
     * 查询即将过期的物料（关联库存表）
     */
    @Select("SELECT DISTINCT m.* FROM inventory_material m " +
            "INNER JOIN inventory_stock s ON m.material_id = s.material_id " +
            "WHERE s.expiry_date <= DATE_ADD(CURDATE(), INTERVAL m.expiry_alert_days DAY) " +
            "AND s.expiry_date IS NOT NULL AND m.status = 'active'")
    List<InventoryMaterial> selectExpiringMaterials();

    /**
     * 更新物料当前库存
     */
    @Update("UPDATE inventory_material SET current_stock = current_stock + #{quantity} " +
            "WHERE material_id = #{materialId}")
    int updateStock(@Param("materialId") Long materialId, @Param("quantity") BigDecimal quantity);

    /**
     * 批量更新物料状态
     */
    @Update("UPDATE inventory_material SET status = #{status}, update_time = NOW() " +
            "WHERE material_id IN (${ids})")
    int batchUpdateStatus(@Param("ids") String ids, @Param("status") String status);

}
