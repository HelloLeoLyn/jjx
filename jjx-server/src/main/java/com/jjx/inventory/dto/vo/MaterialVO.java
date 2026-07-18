package com.jjx.inventory.dto.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 物料视图对象VO
 */
@Data
public class MaterialVO {

    private Long materialId;
    private String materialCode;
    private String materialName;
    private String materialNameEn;
    private String materialType;
    private Long categoryId;
    private String categoryName;
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
    private String defaultWarehouseName;
    private Long defaultLocationId;
    private String defaultLocationName;
    private BigDecimal currentStock;
    private BigDecimal availableStock;
    private BigDecimal reservedStock;
    private BigDecimal inTransitStock;
    private Integer status;
    private String remark;
    private String createBy;
    private String createByName;
    private LocalDateTime createTime;
    private String updateBy;
    private String updateByName;
    private LocalDateTime updateTime;
}
