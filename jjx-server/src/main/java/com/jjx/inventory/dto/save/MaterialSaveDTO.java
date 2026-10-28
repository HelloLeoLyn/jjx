package com.jjx.inventory.dto.save;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 物料新增参数DTO
 */
@Data
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
public class MaterialSaveDTO {

    private String materialCode;
    private String materialName;
    private Long categoryId;
    /** 服务端由属性与分类派生，不接受独立写入。 */
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private Long templateId;

    private String specification;
    private String materialGrade;
    private String model;
    private String color;
    private String brand;
    private String searchKeywords;
    private BigDecimal widthMm;
    private BigDecimal lengthMm;
    private BigDecimal thicknessMm;
    private String sizeUnit;
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
