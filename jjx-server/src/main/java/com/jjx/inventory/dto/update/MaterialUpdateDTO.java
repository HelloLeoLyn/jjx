package com.jjx.inventory.dto.update;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 物料更新参数DTO
 */
@Data
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
public class MaterialUpdateDTO {

    @com.fasterxml.jackson.annotation.JsonIgnore
    @lombok.Setter(lombok.AccessLevel.NONE)
    private java.util.Set<String> suppliedFields = new java.util.HashSet<>();

    private Long materialId;
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

    /** 成本单价（人定，099） */
    private BigDecimal costPrice;

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
    @com.fasterxml.jackson.annotation.JsonSetter("widthMm")
    public void setWidthMm(BigDecimal value) {
        this.widthMm = value;
        suppliedFields.add("widthMm");
    }
    @com.fasterxml.jackson.annotation.JsonSetter("lengthMm")
    public void setLengthMm(BigDecimal value) {
        this.lengthMm = value;
        suppliedFields.add("lengthMm");
    }
    @com.fasterxml.jackson.annotation.JsonSetter("thicknessMm")
    public void setThicknessMm(BigDecimal value) {
        this.thicknessMm = value;
        suppliedFields.add("thicknessMm");
    }
    @com.fasterxml.jackson.annotation.JsonSetter("sizeUnit")
    public void setSizeUnit(String value) {
        this.sizeUnit = value;
        suppliedFields.add("sizeUnit");
    }
    @com.fasterxml.jackson.annotation.JsonSetter("specification")
    public void setSpecification(String value) {
        this.specification = value;
        suppliedFields.add("specification");
    }
}
