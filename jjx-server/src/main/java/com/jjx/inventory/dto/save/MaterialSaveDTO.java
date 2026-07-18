package com.jjx.inventory.dto.save;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 物料新增参数DTO
 */
@Data
public class MaterialSaveDTO {

    private String materialCode;
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
    private String status;
    private String remark;
}
