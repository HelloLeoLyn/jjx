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
    private Integer leadTime;
    private Long supplierId;
    private String supplierName;
    private Long defaultWarehouseId;
    private Long defaultLocationId;
    private Integer status;
    private String remark;
}
