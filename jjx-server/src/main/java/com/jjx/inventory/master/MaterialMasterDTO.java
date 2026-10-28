package com.jjx.inventory.master;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** 完整主数据表单；类型与模板ID由服务端沿分类树解析。 */
@Data
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
public class MaterialMasterDTO {
    private Long materialId;
    private String materialCode;
    private Long categoryId;
    /** 服务端由属性与分类派生，不接受独立写入。 */
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private Long templateId;
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private String thickness;
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private String widthSize;
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private BigDecimal widthMm;
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private BigDecimal lengthMm;
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private BigDecimal thicknessMm;
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private String sizeUnit;
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private String specBasis;
    /** 克重 g/m²：与厚度二选一，填了即判定为克重口径。 */
    private BigDecimal grammage;
    /** 厚度公差 ±mm。 */
    private BigDecimal thicknessTolerance;

    private String materialName;
    private String materialMeta;
    private String materialForm;
    private String model;
    private String color;
    private String brand;
    private String size;
    private String unit;
    private Map<String, Object> attributeJson;
    private Boolean isCustomerSupplied;
    private Boolean isSample;
    private String searchKeywords;
    private String remark;
    private Long supplierId;
    private String supplierName;
    private Long defaultWarehouseId;
    private Long defaultLocationId;
    private BigDecimal safeStock;
    private BigDecimal maxStock;
    private BigDecimal reorderPoint;
    private BigDecimal standardPrice;
    private Integer leadTime;
    private Boolean batchControl;
    private Integer shelfLife;
    private Integer expiryAlertDays;
    private List<Long> tagIds;
}
