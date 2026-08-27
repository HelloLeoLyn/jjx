package com.jjx.inventory.dto.update;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 物料更新参数DTO
 */
@Data
public class MaterialUpdateDTO {

    private Long materialId;
    private String materialName;
    private String materialNameEn;
    private String materialType;
    private Long categoryId;
    private String specification;
    private String unit;
    private BigDecimal unitConv;
    private String unitAlt;
    private Boolean batchControl;
    private Integer shelfLife;
    private Integer expiryAlertDays;
    private BigDecimal safeStock;
    private BigDecimal maxStock;
    private BigDecimal reorderPoint;
    private BigDecimal standardPrice;

    /** 成本单价（人定，099） */
    private BigDecimal costPrice;

    /** 成本单价来源（最近采购价/人工指定/初始，099） */
    private String costPriceFrom;
    private Integer leadTime;
    private Long supplierId;
    private String supplierName;
    private Long defaultWarehouseId;
    private Long defaultLocationId;
    private Integer status;
    private String remark;
}
