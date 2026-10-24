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

    /** 物料分类ID */
    private Long categoryId;

    /** 规格型号/技术参数 */
    private String specification;
    private String materialGrade;
    private String model;
    private String color;
    private String brand;
    private String searchKeywords;
    private String size;

    /** 新模型来源与标准字段；属性JSON是品类特有属性的唯一写入源。 */
    private String originalName;
    private String originalSpecification;
    private String materialMeta;
    private String materialForm;
    private String attributeJson;
    /** 属性保存入口派生；数据库生成列不进入实体写入路径。 */
    private Long templateId;
    private String thickness;
    private String widthSize;
    private Boolean isCustomerSupplied;
    private Boolean isSample;


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

    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private java.util.Set<String> suppliedFields = new java.util.HashSet<>();
}
