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

    @Select("SELECT * FROM inventory_stock WHERE inventory_item_id = #{inventoryItemId}")
    InventoryStock selectByInventoryItemId(@Param("inventoryItemId") Long inventoryItemId);

    @Update("INSERT INTO inventory_stock (inventory_item_id, material_id, material_code, material_name, total_quantity, total_reserved, earliest_expiry, location_id) " +
            "SELECT si.inventory_item_id, MAX(si.material_id), MAX(si.material_code), MAX(si.material_name), " +
            "COALESCE(SUM(si.quantity),0), COALESCE(SUM(si.reserved_quantity),0), MIN(si.expiry_date), " +
            "(SELECT sub.location_id FROM inventory_stock_item sub WHERE sub.inventory_item_id=si.inventory_item_id " +
            "AND sub.status=1 AND sub.quantity>0 ORDER BY (sub.location_id IS NULL), sub.expiry_date, sub.last_inbound_time LIMIT 1) " +
            "FROM inventory_stock_item si WHERE si.inventory_item_id=#{inventoryItemId} AND si.status=1 GROUP BY si.inventory_item_id " +
            "ON DUPLICATE KEY UPDATE total_quantity=VALUES(total_quantity), total_reserved=VALUES(total_reserved), " +
            "earliest_expiry=VALUES(earliest_expiry), location_id=VALUES(location_id), last_update_time=NOW()")
    int refreshSummaryByInventoryItemId(@Param("inventoryItemId") Long inventoryItemId);

    /**
     * 刷新指定材料的汇总数据（兼容按 materialId 调用的存量调用点）
     * 2026-09-10：原实现 INSERT 不写 inventory_item_id，而唯一键 uk_inventory_item(inventory_item_id)
     * 对 NULL 不去重、ON DUPLICATE KEY 对 NULL 行永不触发 → 每调一次就新增一行 inventory_item_id IS NULL 的脏行，
     * 造成库存台账同一物料多行、数量与 inventory_stock_item 不一致（历史脏行已由 79 号迁移清理）。
     * 现改为按 si.inventory_item_id 分组写入（与 refreshSummaryByInventoryItemId 等价），不再产生 NULL 行。
     */
    @Update("INSERT INTO inventory_stock (inventory_item_id, material_id, material_code, material_name, total_quantity, total_reserved, earliest_expiry, location_id) " +
            "SELECT si.inventory_item_id, MAX(si.material_id), MAX(si.material_code), MAX(si.material_name), " +
            "       COALESCE(SUM(si.quantity), 0), " +
            "       COALESCE(SUM(si.reserved_quantity), 0), " +
            "       MIN(si.expiry_date), " +
            "       (SELECT sub.location_id FROM inventory_stock_item sub " +
            "        WHERE sub.inventory_item_id = si.inventory_item_id AND sub.status = 1 AND sub.quantity > 0 " +
            "        ORDER BY (sub.location_id IS NULL) ASC, sub.expiry_date ASC, sub.last_inbound_time ASC LIMIT 1) " +
            "FROM inventory_stock_item si " +
            "WHERE si.material_id = #{materialId} AND si.status = 1 AND si.inventory_item_id IS NOT NULL " +
            "GROUP BY si.inventory_item_id " +
            "ON DUPLICATE KEY UPDATE " +
            "  total_quantity = VALUES(total_quantity), " +
            "  total_reserved = VALUES(total_reserved), " +
            "  earliest_expiry = VALUES(earliest_expiry), " +
            "  location_id = VALUES(location_id), " +
            "  last_update_time = NOW()")
    int refreshSummary(@Param("materialId") Long materialId);

    /**
     * 查询低库存物料（低于安全库存）027/080定稿：用可用量(available_quantity=总量-预留)而非总量
     */
    @Select("SELECT s.*, i.safe_stock FROM inventory_stock s " +
            "JOIN inventory_item i ON s.inventory_item_id = i.inventory_item_id " +
            "WHERE (s.total_quantity - IFNULL(s.total_reserved,0)) < i.safe_stock AND i.safe_stock > 0")
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
     * 2026-09-10：原 `JOIN + GROUP BY s.material_id` 与 `SELECT s.*` 在 ONLY_FULL_GROUP_BY
     * （MySQL 8 默认 sql_mode）下非法（错误 1055 Expression #1 ... not in GROUP BY clause），
     * 导致库存台账呆滞列 / 呆滞预警 / 呆滞分析报表全部 500。
     * 改用 EXISTS 子查询：天然去重（不再需要 GROUP BY），筛选语义保持不变。
     */
    @Select("SELECT s.* FROM inventory_stock s " +
            "WHERE EXISTS (SELECT 1 FROM inventory_stock_item i " +
            "              WHERE i.material_id = s.material_id " +
            "                AND (i.last_outbound_time IS NULL " +
            "                     OR i.last_outbound_time < DATE_SUB(NOW(), INTERVAL 180 DAY)))")
    List<InventoryStock> selectObsolete();
}
