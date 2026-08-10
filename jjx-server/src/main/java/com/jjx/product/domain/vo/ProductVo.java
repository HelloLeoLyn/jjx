package com.jjx.product.domain.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 产品VO
 */
@Data
public class ProductVo {

    /** 产品ID */
    private Long productId;

    /** 产品编码 */
    private String productCode;

    /** 产品名称 */
    private String productName;

    /** 分类ID */
    private Long categoryId;

    /** 分类名称 */
    private String categoryName;

    /** 产品类型 */
    private String productType;

    /** 规格参数（JSON字符串） */
    private String specJson;

    /** 基础售价 */
    private BigDecimal basePrice;

    /** 标准成本 */
    private BigDecimal costPrice;

    /** 最小起订量 */
    private Integer minOrderQty;

    /** 标准交期(天) */
    private Integer leadTime;

    /** 产品状态 */
    private Integer productStatus;

    /** 当前BOM ID */
    private Long currentBomId;

    /**  bom名称 */
    private String bomName;

    /** bom code */
    private String bomCode;

    /** bom version */
    private String bomVersion;

    /** 当前工艺路线ID */
    private Long currentRouteId;

    /** 当前工艺路线name */
    private String routeName;

    /** 当前工艺路线编码 */
    private String routeCode;

    /** 当前工艺路线version */
    private String routeVersion;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 单位 */
    private String unit;
}
