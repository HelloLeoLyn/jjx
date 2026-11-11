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
 * <p>只做 SELECT：BOM 头/行、物料档案、库存汇总、采购行、工单产出供给、仓库校验。所有状态条件由调用方
 * 传入既有枚举值，不在 SQL 里写新的 magic number。库存只读取现有汇总/批次行，不写余额、预留或流水。</p>
 *
 * <p>采购与生产供给按“行”返回而不是在 SQL 里 SUM：部分收货、单位不可比、超收倒挂、审批/生效条件
 * 都需要逐行判断并给出可定位 warning，任何一条不满足的行都必须排除而不是被其他行冲抵
 * （dev-20261111-006 第二步供给口径修复）。</p>
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

    /**
     * 仓库存在性/启停校验（只读）：状态码沿用 StatusEnum，与 InventoryWarehouseServiceImpl.getAllEnabled
     * 的 String.valueOf(StatusEnum.NORMAL.getCode()) 保持同一口径。
     */
    @Select("SELECT warehouse_id, warehouse_code, warehouse_name, status " +
            "FROM inventory_warehouse WHERE warehouse_id = #{warehouseId}")
    WarehouseRow selectWarehouse(@Param("warehouseId") Long warehouseId);

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
     * 该物料是否存在 MATERIAL 产出的制造 BOM（只用于判断“生产供给是否走了 BOM 产出身份兼容读适配”）。
     * current production_order 没有 MATERIAL output 列，产出身份只能经 engineering_bom 核实。
     */
    @Select("SELECT COUNT(*) FROM engineering_bom WHERE output_type = 'MATERIAL' AND output_id = #{materialId}")
    Integer selectMaterialOutputBomCount(@Param("materialId") Long materialId);

    /**
     * 采购行：只取“待收货/部分收货”且“已批准/草稿/待审批”的订单，且预计到货日 ≤ 需求日。
     *
     * <p>不做 SUM：剩余量按行 quantity − received_quantity 计算（下限 0），超收、单位不可比、未批准
     * 都要逐行区分，避免负数或未批准计划冲抵确认供应。已驳回/已取消在 SQL 层直接排除。</p>
     */
    @Select("SELECT o.order_id, o.order_no, o.supplier_name, i.item_id, i.quantity, " +
            "COALESCE(i.received_quantity, 0) AS received_quantity, i.unit, " +
            "o.approval_status, o.receipt_status, o.expected_delivery_date " +
            "FROM purchase_order_item i INNER JOIN purchase_order o ON o.order_id = i.order_id " +
            "WHERE i.material_id = #{materialId} " +
            "AND o.receipt_status IN (#{pendingReceipt}, #{partialReceipt}) " +
            "AND o.approval_status IN (#{approvedApproval}, #{draftApproval}, #{pendingApproval}) " +
            "AND o.expected_delivery_date IS NOT NULL AND o.expected_delivery_date <= #{asOf} " +
            "ORDER BY o.expected_delivery_date, o.order_id, i.item_id")
    List<PurchaseSupplyRow> selectPurchaseRows(@Param("materialId") Long materialId,
                                               @Param("asOf") LocalDate asOf,
                                               @Param("pendingReceipt") Integer pendingReceipt,
                                               @Param("partialReceipt") Integer partialReceipt,
                                               @Param("approvedApproval") Integer approvedApproval,
                                               @Param("draftApproval") Integer draftApproval,
                                               @Param("pendingApproval") Integer pendingApproval);

    /**
     * 物料的生产供给行：只认工单绑定 BOM 的明确产出身份（BOM output_type=MATERIAL），
     * 不用 production_order.product_id 猜物料身份；仅统计未绑定销售订单/上级工单的未分配工单。
     *
     * <p>approval_status=已批准 在 SQL 层强制；BOM 审批/生效区间在服务层逐行判定以便给出 warning。
     * order_status 只取 4 已计划 / 5 待开始 / 6 进行中：8 已完成、9 已取消、10 已关闭、11 已超期
     * 都不按在制产出计入（超期工单实际进度不可核实，计入会与返工/重排重复）。</p>
     */
    @Select("SELECT o.order_id, o.order_no, o.order_status, o.approval_status, o.product_unit, " +
            "o.planned_quantity, o.completed_quantity, o.finished_quantity, o.plan_end_date, " +
            "b.bom_id, b.bom_code, b.approve_status AS bom_approve_status, " +
            "b.effective_date AS bom_effective_date, b.expiry_date AS bom_expiry_date " +
            "FROM production_order o INNER JOIN engineering_bom b ON b.bom_id = o.bom_id " +
            "WHERE b.output_type = 'MATERIAL' AND b.output_id = #{materialId} " +
            "AND o.order_type = 'WORK_ORDER' " +
            "AND o.order_status IN (#{planned}, #{pendingStart}, #{inProgress}) " +
            "AND o.approval_status = #{approvedApproval} " +
            "AND o.sales_order_id IS NULL AND o.parent_order_id IS NULL " +
            "AND o.plan_end_date IS NOT NULL AND o.plan_end_date <= #{asOf} " +
            "ORDER BY o.plan_end_date, o.order_id")
    List<MaterialProductionSupplyRow> selectMaterialProductionRows(@Param("materialId") Long materialId,
                                                                  @Param("asOf") LocalDate asOf,
                                                                  @Param("planned") Integer planned,
                                                                  @Param("pendingStart") Integer pendingStart,
                                                                  @Param("inProgress") Integer inProgress,
                                                                  @Param("approvedApproval") Integer approvedApproval);

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
    class WarehouseRow {
        private Long warehouseId;
        private String warehouseCode;
        private String warehouseName;
        /** char(1)，与 StatusEnum 编码字符串比较。 */
        private String status;
    }

    @Data
    class StockRow {
        private BigDecimal totalQuantity;
        private BigDecimal totalReserved;
    }

    /** 采购行快照：数量单位是 purchase_order_item.unit，不是库存单位。 */
    @Data
    class PurchaseSupplyRow {
        private Long orderId;
        private String orderNo;
        private String supplierName;
        private Long itemId;
        private BigDecimal quantity;
        private BigDecimal receivedQuantity;
        private String unit;
        private Integer approvalStatus;
        private Integer receiptStatus;
        private LocalDate expectedDeliveryDate;
    }

    /** 工单产出供给行：数量单位是 production_order.product_unit。 */
    @Data
    class MaterialProductionSupplyRow {
        private Long orderId;
        private String orderNo;
        private Integer orderStatus;
        private Integer approvalStatus;
        private String productUnit;
        private BigDecimal plannedQuantity;
        private BigDecimal completedQuantity;
        private BigDecimal finishedQuantity;
        private LocalDate planEndDate;
        private Long bomId;
        private String bomCode;
        private Integer bomApproveStatus;
        private LocalDate bomEffectiveDate;
        private LocalDate bomExpiryDate;
    }
}
