package com.jjx.inventory.dto.update;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 物料更新参数DTO
 */
@Data
public class MaterialUpdateDTO {

    @com.fasterxml.jackson.annotation.JsonIgnore
    @lombok.Setter(lombok.AccessLevel.NONE)
    private java.util.Set<String> suppliedFields = new java.util.HashSet<>();

    private Long materialId;
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
    private List<Long> tagIds;
    @com.fasterxml.jackson.annotation.JsonSetter("attributeJson")
    public void setAttributeJson(String value) {
        this.attributeJson = value;
        suppliedFields.add("attributeJson");
    }
    @com.fasterxml.jackson.annotation.JsonSetter("categoryId")
    public void setCategoryId(Long value) {
        this.categoryId = value;
        suppliedFields.add("categoryId");
    }
    @com.fasterxml.jackson.annotation.JsonSetter("materialGrade")
    public void setMaterialGrade(String value) {
        this.materialGrade = value;
        suppliedFields.add("materialGrade");
    }
    @com.fasterxml.jackson.annotation.JsonSetter("model")
    public void setModel(String value) {
        this.model = value;
        suppliedFields.add("model");
    }
    @com.fasterxml.jackson.annotation.JsonSetter("color")
    public void setColor(String value) {
        this.color = value;
        suppliedFields.add("color");
    }
    @com.fasterxml.jackson.annotation.JsonSetter("brand")
    public void setBrand(String value) {
        this.brand = value;
        suppliedFields.add("brand");
    }
    @com.fasterxml.jackson.annotation.JsonSetter("searchKeywords")
    public void setSearchKeywords(String value) {
        this.searchKeywords = value;
        suppliedFields.add("searchKeywords");
    }
    @com.fasterxml.jackson.annotation.JsonSetter("size")
    public void setSize(String value) {
        this.size = value;
        suppliedFields.add("size");
    }
    @com.fasterxml.jackson.annotation.JsonSetter("specification")
    public void setSpecification(String value) {
        this.specification = value;
        suppliedFields.add("specification");
    }
}
