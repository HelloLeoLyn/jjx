package com.jjx.inventory.migration;

import com.jjx.common.exception.BusinessException;
import com.jjx.inventory.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Map;

/** 仅定位迁移凭证和锁；数量变动统一交给 StockMutationService。 */
@Repository
@RequiredArgsConstructor
public class InventoryMigrationRepository {
    private final JdbcTemplate jdbc;
    public Long warehouse(String name, boolean lock) {
        var ids = jdbc.queryForList("SELECT warehouse_id FROM inventory_warehouse WHERE warehouse_name=?" + (lock ? " FOR UPDATE" : ""), Long.class, name);
        if (ids.size() != 1) throw new BusinessException("仓库名称未唯一匹配：" + name);
        return ids.get(0);
    }
    public InventoryInboundOrder batch(String code) {
        return one("SELECT * FROM inventory_inbound_order WHERE inbound_no=?", InventoryInboundOrder.class, code);
    }
    public InventoryInboundOrder lockBatch(Long id) {
        InventoryInboundOrder row = one("SELECT * FROM inventory_inbound_order WHERE inbound_id=? FOR UPDATE", InventoryInboundOrder.class, id);
        if (row == null || !InventoryMigrationData.SOURCE.equals(row.getSourceType())) throw new BusinessException("不是库存迁移批次");
        return row;
    }
    public List<InventoryInboundOrder> batches() {
        return rows("SELECT * FROM inventory_inbound_order WHERE source_type=? ORDER BY inbound_id DESC LIMIT 200", InventoryInboundOrder.class, InventoryMigrationData.SOURCE);
    }
    public List<InventoryInboundItem> lines(Long id) {
        return rows("SELECT * FROM inventory_inbound_item WHERE inbound_id=? ORDER BY sort_order,item_id", InventoryInboundItem.class, id);
    }
    public List<InventoryStorageLocation> locations(Long warehouseId) {
        return rows("SELECT * FROM inventory_storage_location WHERE warehouse_id=?", InventoryStorageLocation.class, warehouseId);
    }
    public List<InventoryStockItem> stocks(InventoryInboundItem line, boolean lock) {
        return rows("SELECT * FROM inventory_stock_item WHERE inventory_item_id=? AND batch_no=? ORDER BY item_id" + (lock ? " FOR UPDATE" : ""), InventoryStockItem.class, line.getInventoryItemId(), line.getBatchNo());
    }
    public List<InventoryTransaction> flows(InventoryInboundItem line) {
        return rows("SELECT * FROM inventory_transaction WHERE inventory_item_id=? AND batch_no=? ORDER BY transaction_id", InventoryTransaction.class, line.getInventoryItemId(), line.getBatchNo());
    }
    public List<Map<String,Object>> configReference() {
        return jdbc.queryForList("SELECT c.category_id,c.category_code,c.category_name,c.model_prefix,t.attribute_schema FROM inventory_material_category c LEFT JOIN inventory_attribute_template t ON t.category_id=c.category_id");
    }
    /** 清库可能保留统一库存身份，迁移新档案不能复用这些旧来源ID。 */
    public Long nextMaterialId() {
        return jdbc.queryForObject("SELECT GREATEST(COALESCE((SELECT MAX(material_id) FROM inventory_material),0),COALESCE((SELECT MAX(source_id) FROM inventory_item WHERE item_type='MATERIAL'),0))+1",Long.class);
    }
    public Long materialCodeFloor() {
        return jdbc.queryForObject("SELECT COALESCE(MAX(CAST(REGEXP_SUBSTR(item_code,'[0-9]+$') AS UNSIGNED)),0)+1 FROM inventory_item WHERE item_type='MATERIAL' AND item_code REGEXP '^[A-Z]+[0-9]+$'",Long.class);
    }
    private <T> T one(String sql, Class<T> type, Object... args) {
        List<T> found = rows(sql, type, args);
        if (found.size()>1) throw new BusinessException("来源记录不唯一，请先核查");
        return found.isEmpty() ? null : found.get(0);
    }
    private <T> List<T> rows(String sql, Class<T> type, Object... args) {
        return jdbc.query(sql, BeanPropertyRowMapper.newInstance(type), args);
    }
}
