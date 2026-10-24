package com.jjx.inventory.dto.save;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

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
    /** 服务端由属性与分类派生，不接受独立写入。 */
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private Long templateId;
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private String thickness;
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private String widthSize;

    private String specification;
    private String materialGrade;
    private String model;
    private String color;
    private String brand;
    private String searchKeywords;
    private String size;
    private String attributeJson;

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
    private List<Long> tagIds;
}
