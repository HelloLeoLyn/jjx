package com.jjx.inventory.location;

import com.jjx.inventory.domain.InventoryStorageLocation;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;

@Mapper
public interface LocationHierarchyMapper {
    @Select("SELECT warehouse_id FROM inventory_warehouse WHERE warehouse_id=#{id} FOR UPDATE")
    Long lockWarehouse(Long id);

    @Select("SELECT * FROM inventory_storage_location WHERE warehouse_id=#{warehouseId} ORDER BY sort_order,location_id")
    List<InventoryStorageLocation> locations(Long warehouseId);

    @Select("SELECT COUNT(*) FROM inventory_storage_location WHERE parent_id=#{id}")
    long children(Long id);

    @Select("SELECT COUNT(*) FROM inventory_stock_item WHERE location_id=#{id} AND (quantity != 0 OR reserved_quantity != 0)")
    long occupied(Long id);

    @Select("SELECT COUNT(*) FROM inventory_stock_item WHERE location_id=#{id}")
    long batches(Long id);

    @Select("SELECT COUNT(*) FROM inventory_transaction WHERE location_id=#{id}")
    long history(Long id);

    @Select("SELECT COUNT(*) FROM inventory_item WHERE default_location_id=#{id}")
    long defaults(Long id);

    @Select("SELECT COUNT(*) FROM inventory_stock WHERE location_id=#{id}")
    long summaryReferences(Long id);

    @Select("SELECT lot_id FROM inventory_inbound_item WHERE item_id=#{sourceItemId}")
    Long sourceLot(Long sourceItemId);

    @Select("SELECT DISTINCT lot_id FROM inventory_transaction WHERE stock_item_id=#{stockItemId} AND lot_id IS NOT NULL LIMIT 2")
    List<Long> stockLots(Long stockItemId);

    @Select("SELECT COUNT(*) FROM inventory_inbound_item WHERE location_id=#{id}")
    long inboundReferences(Long id);

    @Select("SELECT COUNT(*) FROM inventory_outbound_item WHERE location_id=#{id}")
    long outboundReferences(Long id);

    @Select("SELECT COUNT(*) FROM inventory_transfer_item WHERE from_location_id=#{id} OR to_location_id=#{id}")
    long transferReferences(Long id);

    @Select("SELECT COALESCE(MAX(CAST(SUBSTRING(location_code,LENGTH(#{prefix})+1) AS UNSIGNED)),0) FROM inventory_storage_location WHERE location_code LIKE CONCAT(#{prefix},'%') AND SUBSTRING(location_code,LENGTH(#{prefix})+1) REGEXP '^[0-9]+$'")
    long codeFloor(String prefix);

    @Select("SELECT si.location_id,i.unit,SUM(si.quantity) AS quantity,SUM(si.reserved_quantity) AS reserved FROM inventory_stock_item si JOIN inventory_item i ON i.inventory_item_id=si.inventory_item_id WHERE si.warehouse_id=#{warehouseId} AND (si.quantity != 0 OR si.reserved_quantity != 0) GROUP BY si.location_id,i.unit")
    List<Map<String,Object>> balances(Long warehouseId);

    @Select("SELECT t.location_id,i.unit,SUM(CASE WHEN t.quantity>0 THEN t.quantity ELSE 0 END) AS received,SUM(CASE WHEN t.quantity<0 THEN -t.quantity ELSE 0 END) AS issued FROM inventory_transaction t JOIN inventory_item i ON i.inventory_item_id=t.inventory_item_id WHERE t.warehouse_id=#{warehouseId} GROUP BY t.location_id,i.unit")
    List<Map<String,Object>> flows(Long warehouseId);

    @Select("SELECT s.item_id,s.inventory_item_id,s.material_code,s.material_name,s.batch_no,s.quantity,s.reserved_quantity,s.unit_cost,s.roll_no,s.source_inbound_item_id,i.unit FROM inventory_stock_item s JOIN inventory_item i ON i.inventory_item_id=s.inventory_item_id WHERE s.location_id=#{id} AND (s.quantity != 0 OR s.reserved_quantity != 0) ORDER BY s.item_id LIMIT 501")
    List<Map<String,Object>> inventory(Long id);
}
