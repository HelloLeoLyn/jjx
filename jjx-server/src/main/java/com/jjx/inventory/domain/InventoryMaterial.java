package com.jjx.inventory.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.jjx.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 物料主表实体类
 * 对应表：inventory_material
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inventory_material")
public class InventoryMaterial extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 物料ID */
    @TableId(type = IdType.AUTO)
    private Long materialId;

    /** 关联产品ID(成品物料) */
    private Long productId;

    /** 物料编码 */
    private String materialCode;

    /** 物料名称 */
    private String materialName;

    /** 英文名称 */
    private String materialNameEn;

    /** 物料类型：R原材料/S半成品/F成品/A辅助材料 */
    private String materialType;

    /** 物料分类ID */
    private Long categoryId;

    /** 规格型号/技术参数 */
    private String specification;

    /** 基本计量单位 */
    private String unit;

    /** 换算系数（辅助单位与基本单位的换算） */
    private BigDecimal unitConv;

    /** 辅助计量单位 */
    private String unitAlt;

    /** 是否启用批次管理：0否 1是 */
    private Boolean batchControl;

    /** 保质期天数 */
    private Integer shelfLife;

    /** 保质期预警提前天数 */
    private Integer expiryAlertDays;

    /** 安全库存数量 */
    private BigDecimal safeStock;

    /** 最高库存数量 */
    private BigDecimal maxStock;

    /** 再订货点 */
    private BigDecimal reorderPoint;

    /** 标准采购单价 */
    private BigDecimal standardPrice;

    /** 成本单价（人定，099定稿：初始=最近采购价，采购价变化→提醒人工确认，不自动覆盖） */
    private BigDecimal costPrice;

    /** 成本单价来源（最近采购价/人工指定/初始，099） */
    private String costPriceFrom;

    /** 成本单价确认时间（099） */
    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private java.time.LocalDateTime costPriceTime;

    /** 采购提前期(天) */
    private Integer leadTime;

    /** 主要供应商ID */
    private Long supplierId;

    /** 主要供应商名称 */
    private String supplierName;

    /** 默认仓库ID */
    private Long defaultWarehouseId;

    /** 默认库位ID */
    private Long defaultLocationId;

    private Integer status;

    private String processGroup;
}
