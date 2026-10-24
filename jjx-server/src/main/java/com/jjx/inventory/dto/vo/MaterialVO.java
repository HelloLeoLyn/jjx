package com.jjx.inventory.dto.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import com.jjx.system.domain.entity.SysTag;

/**
 * 物料视图对象VO
 */
@Data
public class MaterialVO {

    private Long materialId;
    private String materialCode;
    private String materialName;
    private Long categoryId;
    private String categoryName;
    private String classSource;
    private Boolean classConfirmed;
    /** 分类根中文名，未归类时为空。 */
    private String categoryRootName;
    private String rootType;
    private String specification;
    private String searchKeywords;
    private String size;
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

    private String materialGrade;
    private String model;
    private String color;
    private String brand;
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
    private List<SysTag> tags;
}
