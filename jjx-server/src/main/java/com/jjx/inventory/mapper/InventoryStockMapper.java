package com.jjx.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.inventory.domain.InventoryStock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 库存汇总表 Mapper
 */
@Mapper
public interface InventoryStockMapper extends BaseMapper<InventoryStock> {

    /**
     * 根据物料ID查询汇总记录
     */
    @Select("SELECT * FROM inventory_stock WHERE material_id = #{materialId}")
    InventoryStock selectByMaterialId(@Param("materialId") Long materialId);

    /**
     * 刷新指定物料的汇总数据
     * 从明细表计算汇总值并更新到汇总表
     */
    @Update("UPDATE inventory_stock s " +
            "JOIN ( " +
            "  SELECT " +
            "    #{materialId} AS material_id, " +
            "    COALESCE(SUM(quantity), 0) AS total_qty, " +
            "    COALESCE(SUM(reserved_quantity), 0) AS total_res, " +
            "    MIN(expiry_date) AS earliest_exp, " +
            "    (SELECT location_id FROM inventory_stock_item sub " +
            "     WHERE sub.material_id = #{materialId} AND sub.status = 1 AND sub.quantity > 0 " +
            "     ORDER BY sub.expiry_date ASC, sub.last_inbound_time ASC LIMIT 1) AS loc_id " +
            "  FROM inventory_stock_item " +
            "  WHERE material_id = #{materialId} AND status = 1 " +
            ") t ON s.material_id = t.material_id " +
            "SET s.total_quantity = t.total_qty, " +
            "    s.total_reserved = t.total_res, " +
            "    s.earliest_expiry = t.earliest_exp, " +
            "    s.location_id = t.loc_id")
    int refreshSummary(@Param("materialId") Long materialId);

    /**
     * 查询低库存物料（低于安全库存）
     */
    @Select("SELECT s.* FROM inventory_stock s " +
            "JOIN inventory_material m ON s.material_id = m.material_id " +
            "WHERE s.total_quantity < m.safe_stock AND m.safe_stock > 0")
    List<InventoryStock> selectLowStock();

    /**
     * 查询临期物料（30天内过期）
     */
    @Select("SELECT s.* FROM inventory_stock s " +
            "WHERE s.earliest_expiry IS NOT NULL " +
            "AND s.earliest_expiry <= DATE_ADD(CURDATE(), INTERVAL 30 DAY) " +
            "AND s.earliest_expiry >= CURDATE()")
    List<InventoryStock> selectExpiring();

    /**
     * 查询呆滞物料（180天未出库）
     */
    @Select("SELECT s.* FROM inventory_stock s " +
            "JOIN inventory_stock_item i ON s.material_id = i.material_id " +
            "WHERE i.last_outbound_time IS NULL " +
            "   OR i.last_outbound_time < DATE_SUB(NOW(), INTERVAL 180 DAY) " +
            "GROUP BY s.material_id")
    List<InventoryStock> selectObsolete();
}
