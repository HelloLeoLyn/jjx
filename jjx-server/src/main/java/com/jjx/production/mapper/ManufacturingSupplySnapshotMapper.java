package com.jjx.production.mapper;

import com.jjx.production.domain.model.ManufacturingDemandSnapshot;
import lombok.Data;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 多层需求试算的只读数据入口（dev-20261111-006）。
 *
 * <p>只做 SELECT：BOM 头/行、物料档案、库存汇总、采购在途、工单产出供给。所有状态条件由调用方
 * 传入既有枚举值，不在 SQL 里写新的 magic number。库存只读取现有汇总/批次行，不写余额、预留或流水。</p>
 */
@Mapper
public interface ManufacturingSupplySnapshotMapper {

    @Select("SELECT bom_id, bom_code, bom_name, bom_type, product_id, output_type, output_id, " +
            "approve_status, is_current, effective_date, expiry_date " +
            "FROM engineering_bom WHERE bom_id = #{bomId}")
    ManufacturingDemandSnapshot selectBom(@Param("bomId") Long bomId);

    /**
     * 产品默认根 BOM：优先 is_current，其次最新创建。applied 状态由调用方传 ProductEnums.BomStatus.APPROVED。
     * 历史 output_type 为空按 PRODUCT 兼容（dev-20261111-004 口径）。
     */
    @Select("SELECT bom_id, bom_code, bom_name, bom_type, product_id, output_type, output_id, " +
            "approve_status, is_current, effective_date, expiry_date " +
            "FROM engineering_bom WHERE product_id = #{productId} AND approve_status = #{approvedStatus} " +
            "AND (output_type IS NULL OR output_type = 'PRODUCT') " +
            "ORDER BY is_current DESC, create_time DESC, bom_id DESC LIMIT 1")
    ManufacturingDemandSnapshot selectDefaultProductBom(@Param("productId") Long productId,
                                                        @Param("approvedStatus") Integer approvedStatus);

    /** loss_rate 按 BigDecimal 读取，避免旧实体 Integer 截断小数损耗（dev-20261111-006）。 */
    @Select("SELECT item_id, bom_id, parent_material_id, material_id, material_code, material_name, unit, " +
            "module_qty, base_qty, loss_rate, min_issue_qty, source_type, sub_bom_id, " +
            "unit_factor, unit_factor_basis, unit_factor_source, width_mm, length_mm, item_order, review_status " +
            "FROM engineering_bom_item WHERE bom_id = #{bomId} ORDER BY item_order, item_id")
    List<ManufacturingDemandSnapshot.Line> selectBomLines(@Param("bomId") Long bomId);

    @Select("SELECT product_id, product_code, product_name, unit, current_bom_id FROM product " +
            "WHERE product_id = #{productId}")
    ProductRow selectProduct(@Param("productId") Long productId);

    @Select("SELECT material_id, material_code, material_name, unit, unit_alt, unit_conv, safe_stock, " +
            "width_mm, length_mm, status FROM inventory_material WHERE material_id = #{materialId}")
    MaterialRow selectMaterial(@Param("materialId") Long materialId);

    @Select("SELECT COALESCE(SUM(total_quantity), 0) AS total_quantity, " +
            "COALESCE(SUM(total_reserved), 0) AS total_reserved " +
            "FROM inventory_stock WHERE material_id = #{materialId}")
    StockRow selectMaterialStock(@Param("materialId") Long materialId);

    @Select("SELECT COALESCE(SUM(quantity), 0) AS total_quantity, " +
            "COALESCE(SUM(reserved_quantity), 0) AS total_reserved " +
            "FROM inventory_stock_item WHERE material_id = #{materialId} " +
            "AND warehouse_id = #{warehouseId} AND status = #{normalStatus}")
    StockRow selectWarehouseStock(@Param("materialId") Long materialId,
                                  @Param("warehouseId") Long warehouseId,
                                  @Param("normalStatus") Integer normalStatus);

    /**
     * 采购在途：剩余数量按预计到货日 ≤ 需求日；无预计到货日不覆盖有日期需求。
     * approval_status 复用 ApproveStatusEnum 的草稿/待审批/已批准；排除已驳回/已取消。
     */
    @Select("SELECT COALESCE(SUM(i.quantity - COALESCE(i.received_quantity, 0)), 0) " +
            "FROM purchase_order_item i INNER JOIN purchase_order o ON o.order_id = i.order_id " +
            "WHERE i.material_id = #{materialId} AND o.receipt_status = #{pendingReceipt} " +
            "AND o.approval_status IN (#{draftApproval}, #{pendingApproval}, #{approvedApproval}) " +
            "AND o.expected_delivery_date IS NOT NULL AND o.expected_delivery_date <= #{asOf}")
    BigDecimal selectPurchaseSupply(@Param("materialId") Long materialId,
                                    @Param("asOf") LocalDate asOf,
                                    @Param("pendingReceipt") Integer pendingReceipt,
                                    @Param("draftApproval") Integer draftApproval,
                                    @Param("pendingApproval") Integer pendingApproval,
                                    @Param("approvedApproval") Integer approvedApproval);

    /**
     * 物料的生产供给兼容读适配：只认工单绑定 BOM 的明确产出身份（BOM output_type=MATERIAL），
     * 不用 production_order.product_id 猜物料身份（当前表无 MATERIAL output 列）。
     * 仅统计未绑定销售订单/上级工单的未分配工单，避免把已承担其他需求的产出去重后重复计入。
     */
    @Select("SELECT COALESCE(SUM(o.remaining_quantity), 0) " +
            "FROM production_order o INNER JOIN engineering_bom b ON b.bom_id = o.bom_id " +
            "WHERE b.output_type = 'MATERIAL' AND b.output_id = #{materialId} " +
            "AND o.order_type = 'WORK_ORDER' " +
            "AND o.order_status IN (#{planned}, #{pendingStart}, #{inProgress}) " +
            "AND o.sales_order_id IS NULL AND o.parent_order_id IS NULL " +
            "AND o.plan_end_date IS NOT NULL AND o.plan_end_date <= #{asOf} " +
            "AND o.remaining_quantity > 0")
    BigDecimal selectMaterialProductionSupply(@Param("materialId") Long materialId,
                                              @Param("asOf") LocalDate asOf,
                                              @Param("planned") Integer planned,
                                              @Param("pendingStart") Integer pendingStart,
                                              @Param("inProgress") Integer inProgress);

    @Data
    class ProductRow {
        private Long productId;
        private String productCode;
        private String productName;
        private String unit;
        private Long currentBomId;
    }

    @Data
    class MaterialRow {
        private Long materialId;
        private String materialCode;
        private String materialName;
        private String unit;
        private String unitAlt;
        private BigDecimal unitConv;
        private BigDecimal safeStock;
        private BigDecimal widthMm;
        private BigDecimal lengthMm;
        private Integer status;
    }

    @Data
    class StockRow {
        private BigDecimal totalQuantity;
        private BigDecimal totalReserved;
    }
}
