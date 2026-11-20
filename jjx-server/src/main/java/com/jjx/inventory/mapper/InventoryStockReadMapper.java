package com.jjx.inventory.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.dto.query.StockQueryDTO;
import com.jjx.inventory.dto.vo.StockVO;
import org.apache.ibatis.annotations.*;
import java.util.*;

/** 即时库存同范围查询：先在数据库聚合有效批次，再筛选/分页/汇总。 */
@Mapper
public interface InventoryStockReadMapper {
    String ROWS = """
        SELECT s.stock_id,s.inventory_item_id,s.material_id,i.item_type,i.source_id,
          COALESCE(m.material_code,i.item_code,s.material_code) material_code,
          COALESCE(m.material_name,i.item_name,s.material_name) material_name,
          COALESCE(m.specification,i.specification) specification,i.unit,
          q.total_quantity,q.total_reserved,q.total_quantity-q.total_reserved available_quantity,
          q.total_cost,CASE WHEN q.total_quantity>0 THEN q.total_cost/q.total_quantity ELSE 0 END avg_unit_cost,
          q.missing_cost_batches,q.earliest_expiry,s.last_update_time update_time,i.create_time,
          COALESCE(i.safe_stock,0) safe_stock,
          CASE WHEN COALESCE(i.safe_stock,0)>0 AND q.total_quantity-q.total_reserved&lt;i.safe_stock THEN 1 ELSE 0 END low_stock,
          CASE WHEN q.earliest_expiry IS NOT NULL AND q.earliest_expiry&lt;=TIMESTAMPADD(DAY,30,CURRENT_DATE) THEN 1 ELSE 0 END expiring,
          CASE WHEN q.total_quantity>0 AND q.last_movement&lt;TIMESTAMPADD(DAY,-180,CURRENT_TIMESTAMP) THEN 1 ELSE 0 END obsolete,
          (SELECT b.location_id FROM inventory_stock_item b WHERE b.inventory_item_id=s.inventory_item_id
             AND b.status=#{activeStatus} AND b.quantity>0
             <if test='query.warehouseId != null'>AND b.warehouse_id=#{query.warehouseId}</if>
             <if test='query.locationId != null'>AND b.location_id=#{query.locationId}</if>
             ORDER BY (b.location_id IS NULL), b.expiry_date, b.item_id LIMIT 1) location_id
        FROM inventory_stock s JOIN inventory_item i ON i.inventory_item_id=s.inventory_item_id
        LEFT JOIN inventory_material m ON i.item_type=#{materialType} AND m.material_id=i.source_id
        JOIN (SELECT b.inventory_item_id,SUM(b.quantity) total_quantity,
                SUM(COALESCE(b.reserved_quantity,0)) total_reserved,
                COALESCE(SUM(b.quantity*b.unit_cost),0) total_cost,
                SUM(CASE WHEN b.quantity>0 AND b.unit_cost IS NULL THEN 1 ELSE 0 END) missing_cost_batches,
                MIN(CASE WHEN b.quantity>0 THEN b.expiry_date END) earliest_expiry,
                MAX(CASE WHEN b.last_outbound_time IS NULL THEN COALESCE(b.last_inbound_time,b.create_time) WHEN b.last_inbound_time IS NULL THEN b.last_outbound_time ELSE GREATEST(b.last_outbound_time,b.last_inbound_time) END) last_movement
              FROM inventory_stock_item b WHERE b.status=#{activeStatus}
              <if test='query.warehouseId != null'>AND b.warehouse_id=#{query.warehouseId}</if>
              <if test='query.locationId != null'>AND b.location_id=#{query.locationId}</if>
              GROUP BY b.inventory_item_id) q ON q.inventory_item_id=s.inventory_item_id
        """;
    String FROM = " FROM (" + ROWS + ") v ${ew.customSqlSegment}";

    @Select("<script>SELECT v.*" + FROM + "</script>")
    IPage<StockVO> page(Page<StockVO> page, @Param("query") StockQueryDTO query,
        @Param("ew") Wrapper<?> filter, @Param("materialFilter") LambdaQueryWrapper<InventoryMaterial> materialFilter,
        @Param("activeStatus") Integer activeStatus, @Param("materialType") String materialType);

    @Select("<script>SELECT COUNT(*) material_count,COALESCE(SUM(v.total_quantity),0) total_quantity,"
        + "COALESCE(SUM(v.total_reserved),0) total_reserved,COALESCE(SUM(v.total_cost),0) total_cost,"
        + "COALESCE(SUM(v.missing_cost_batches),0) missing_cost_batches,COALESCE(SUM(v.low_stock),0) low_count"
        + FROM + "</script>")
    Map<String,Object> summary(@Param("query") StockQueryDTO query, @Param("ew") Wrapper<?> filter,
        @Param("materialFilter") LambdaQueryWrapper<InventoryMaterial> materialFilter,
        @Param("activeStatus") Integer activeStatus,@Param("materialType") String materialType);

    @Select("<script>SELECT COALESCE(units.unit,'未设单位') unit,SUM(units.total_quantity) quantity FROM (SELECT v.*"
        + FROM + ") units GROUP BY unit ORDER BY unit</script>")
    List<Map<String,Object>> quantities(@Param("query") StockQueryDTO query, @Param("ew") Wrapper<?> filter,
        @Param("materialFilter") LambdaQueryWrapper<InventoryMaterial> materialFilter,
        @Param("activeStatus") Integer activeStatus,@Param("materialType") String materialType);
}
