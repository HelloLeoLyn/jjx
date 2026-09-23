package com.jjx.inventory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jjx.inventory.domain.InventoryTransaction;
import com.jjx.inventory.dto.query.TransactionQueryDTO;
import com.jjx.inventory.dto.vo.TransactionVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 库存流水Mapper接口
 */
@Mapper
public interface InventoryTransactionMapper extends BaseMapper<InventoryTransaction> {

    /**
     * 收发明细分页：流水为唯一数据源，只补仓库/库位展示名。
     */
    @Select({
            "<script>",
            "SELECT t.*, w.warehouse_name AS warehouseName, l.location_name AS locationName ",
            "FROM inventory_transaction t ",
            "LEFT JOIN inventory_warehouse w ON w.warehouse_id = t.warehouse_id ",
            "LEFT JOIN inventory_storage_location l ON l.location_id = t.location_id ",
            "<where>",
            "<if test='query.transactionId != null'> AND t.transaction_id = #{query.transactionId}</if>",
            "<if test='query.inventoryItemId != null'> AND t.inventory_item_id = #{query.inventoryItemId}</if>",
            "<if test='query.materialId != null'> AND t.material_id = #{query.materialId}</if>",
            "<if test='query.materialCode != null and query.materialCode != \"\"'> AND t.material_code LIKE CONCAT('%', #{query.materialCode}, '%')</if>",
            "<if test='query.materialName != null and query.materialName != \"\"'> AND t.material_name LIKE CONCAT('%', #{query.materialName}, '%')</if>",
            "<if test='query.warehouseId != null'> AND t.warehouse_id = #{query.warehouseId}</if>",
            "<if test='query.transactionType != null and query.transactionType != \"\"'> AND UPPER(t.transaction_type) = UPPER(#{query.transactionType})</if>",
            "<if test='query.sourceType != null and query.sourceType != \"\"'>",
            "<choose>",
            "<when test='query.sourceType == \"purchase\"'> AND UPPER(t.source_type) IN ('PURCHASE', 'PURCHASE_ORDER')</when>",
            "<when test='query.sourceType == \"production\"'> AND UPPER(t.source_type) IN ('PRODUCTION', 'WORK_ORDER')</when>",
            "<when test='query.sourceType == \"sales\"'> AND UPPER(t.source_type) IN ('SALES', 'SALES_ORDER')</when>",
            "<otherwise> AND UPPER(t.source_type) = UPPER(#{query.sourceType})</otherwise>",
            "</choose>",
            "</if>",
            "<if test='query.sourceNo != null and query.sourceNo != \"\"'> AND t.source_no LIKE CONCAT('%', #{query.sourceNo}, '%')</if>",
            "<if test='query.batchNo != null and query.batchNo != \"\"'> AND t.batch_no LIKE CONCAT('%', #{query.batchNo}, '%')</if>",
            "<if test='query.transactionTimeStart != null'> AND t.transaction_time &gt;= #{query.transactionTimeStart}</if>",
            "<if test='query.transactionTimeEnd != null'> AND t.transaction_time &lt; #{query.transactionTimeEnd}</if>",
            "</where>",
            "ORDER BY t.transaction_time DESC, t.transaction_id DESC",
            "</script>"
    })
    Page<TransactionVO> selectTransactionPage(Page<TransactionVO> page,
                                               @Param("query") TransactionQueryDTO query);

    /**
     * 查询指定物料的流水记录
     */
    @Select("SELECT * FROM inventory_transaction WHERE material_id = #{materialId} " +
            "ORDER BY transaction_time DESC LIMIT #{limit}")
    List<InventoryTransaction> selectByMaterial(@Param("materialId") Long materialId,
                                                 @Param("limit") int limit);

    /**
     * 按批次查流水（dev-20260923-017）：批次明细「变动流水」抽屉用。
     * 走 idx_batch_no / idx_transaction_inventory_item 索引，按时间正序（便于看余额演变）。
     */
    @Select("SELECT * FROM inventory_transaction " +
            "WHERE inventory_item_id = #{inventoryItemId} AND batch_no = #{batchNo} " +
            "ORDER BY transaction_time ASC, transaction_id ASC")
    List<InventoryTransaction> selectByBatch(@Param("inventoryItemId") Long inventoryItemId,
                                              @Param("batchNo") String batchNo);

    /**
     * 批次收发存聚合（dev-20260923-017）：以流水为唯一真源——入库合计 / 出库合计 / 结存。
     * 不依赖批次表任何新增字段（避免第二真源）；正数为收、负数为发，结存 = 代数和。
     */
    @Select("SELECT batch_no AS batchNo, " +
            "COALESCE(SUM(CASE WHEN quantity > 0 THEN quantity ELSE 0 END), 0) AS receivedQuantity, " +
            "COALESCE(SUM(CASE WHEN quantity < 0 THEN -quantity ELSE 0 END), 0) AS issuedQuantity, " +
            "COALESCE(SUM(quantity), 0) AS balanceQuantity " +
            "FROM inventory_transaction WHERE inventory_item_id = #{inventoryItemId} " +
            "GROUP BY batch_no")
    List<Map<String, Object>> selectBatchFlowSummary(@Param("inventoryItemId") Long inventoryItemId);

    /**
     * 查询指定时间范围内的流水
     */
    @Select("SELECT * FROM inventory_transaction WHERE transaction_time BETWEEN #{startTime} AND #{endTime} " +
            "ORDER BY transaction_time DESC")
    List<InventoryTransaction> selectByTimeRange(@Param("startTime") LocalDateTime startTime,
                                                  @Param("endTime") LocalDateTime endTime);

    /**
     * 统计指定物料的出入库数量
     */
    @Select("SELECT transaction_type, SUM(quantity) as total_quantity " +
            "FROM inventory_transaction WHERE material_id = #{materialId} " +
            "AND transaction_time >= #{startTime} GROUP BY transaction_type")
    List<InventoryTransaction> statByMaterial(@Param("materialId") Long materialId,
                                               @Param("startTime") LocalDateTime startTime);

}
