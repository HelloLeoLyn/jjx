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

    /** 金额由有效批次的结存×成本聚合；未知成本独立计数，不能伪装为真实零成本。 */
    @Select({"<script>",
            "SELECT COALESCE(SUM(quantity*unit_cost),0) AS known_cost,",
            "COALESCE(SUM(CASE WHEN unit_cost IS NULL AND quantity &gt; 0 THEN 1 ELSE 0 END),0) AS missing_cost_batches",
            "FROM inventory_stock_item WHERE status=#{activeStatus} AND inventory_item_id IN",
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "</script>"})
    java.util.Map<String,Object> costSummary(@Param("ids") List<Long> ids, @Param("activeStatus") Integer activeStatus);

    @Select({"<script>","SELECT COALESCE(i.unit,'未设单位') AS unit,SUM(s.quantity) AS quantity",
            "FROM inventory_stock_item s JOIN inventory_item i ON i.inventory_item_id=s.inventory_item_id",
            "WHERE s.status=#{activeStatus} AND s.inventory_item_id IN",
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>",
            "GROUP BY i.unit ORDER BY i.unit","</script>"})
    List<java.util.Map<String,Object>> quantitiesByUnit(@Param("ids") List<Long> ids, @Param("activeStatus") Integer activeStatus);

    /**
     * 根据物料ID查询汇总记录
     */
    @Select("SELECT * FROM inventory_stock WHERE material_id = #{materialId}")
    InventoryStock selectByMaterialId(@Param("materialId") Long materialId);

    /**
     * 按物料汇总（跨库位合计）：库存汇总已按「物料+库位」拆行，
     * 该查询保持原有单行返回语义，供可用量/预留等总量判断使用（dev-20261125-003）。
     */
    @Select("SELECT inventory_item_id, MAX(material_id) AS material_id, MAX(material_code) AS material_code, " +
            "MAX(material_name) AS material_name, COALESCE(SUM(total_quantity),0) AS total_quantity, " +
            "COALESCE(SUM(total_reserved),0) AS total_reserved, COALESCE(SUM(available_quantity),0) AS available_quantity, " +
            "MIN(earliest_expiry) AS earliest_expiry, MAX(location_id) AS location_id " +
            "FROM inventory_stock WHERE inventory_item_id = #{inventoryItemId} GROUP BY inventory_item_id")
    InventoryStock selectByInventoryItemId(@Param("inventoryItemId") Long inventoryItemId);

    /** 锁定该物料的全部库位汇总行（发货建单串行校验用）。 */
    @Select("SELECT * FROM inventory_stock WHERE inventory_item_id = #{inventoryItemId} FOR UPDATE")
    List<InventoryStock> lockByInventoryItemId(@Param("inventoryItemId") Long inventoryItemId);

    /** 按「物料 + 库位」刷新汇总（dev-20261125-003）。 */
    @Update("INSERT INTO inventory_stock (inventory_item_id, material_id, material_code, material_name, total_quantity, total_reserved, earliest_expiry, location_id) " +
            "SELECT si.inventory_item_id, MAX(si.material_id), MAX(si.material_code), MAX(si.material_name), " +
            "COALESCE(SUM(si.quantity),0), COALESCE(SUM(si.reserved_quantity),0), MIN(si.expiry_date), si.location_id " +
            "FROM inventory_stock_item si WHERE si.inventory_item_id=#{inventoryItemId} AND si.status=1 AND si.location_id IS NOT NULL " +
            "GROUP BY si.inventory_item_id, si.location_id " +
            "ON DUPLICATE KEY UPDATE total_quantity=VALUES(total_quantity), total_reserved=VALUES(total_reserved), " +
            "earliest_expiry=VALUES(earliest_expiry), last_update_time=NOW()")
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
            "       MIN(si.expiry_date), si.location_id " +
            "FROM inventory_stock_item si " +
            "WHERE si.material_id = #{materialId} AND si.status = 1 AND si.inventory_item_id IS NOT NULL AND si.location_id IS NOT NULL " +
            "GROUP BY si.inventory_item_id, si.location_id " +
            "ON DUPLICATE KEY UPDATE " +
            "  total_quantity = VALUES(total_quantity), " +
            "  total_reserved = VALUES(total_reserved), " +
            "  earliest_expiry = VALUES(earliest_expiry), " +
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
