package com.jjx.product.domain.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 产品VO
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductDTO {

    /** 产品ID */
    private Long productId;

    /** 产品编码 */
    private String productCode;

    /** 产品名称 */
    @NotBlank(message = "产品名称不能为空")
    private String productName;

    /** 编码构成：面板类型（dev-20260918：产品编码由后端按 客户简称+流水号+面板+线路 生成） */
    private String panelType;
    /** 编码构成：面板特征 */
    private String panelFeature;
    /** 编码构成：线路类型 */
    private String circuitType;
    /** 编码构成：线路特征 */
    private String circuitFeature;

    /** 分类ID */
    private Long categoryId;

    /** 分类编码 */
    private String categoryCode;

    /** 客户ID（2026-08-10 产品关联客户） */
    private Long customerId;

    /** 客户名称（冗余） */
    private String customerName;

    /** 产品类型 */
    private String productType;

    /** 规格参数（JSON字符串） */
    private String specJson;

    /** 基础售价 */
    private Double basePrice;

    /** 标准成本 */
    private Double costPrice;

    /** 最小起订量 */
    private Integer minOrderQty;

    /** 标准交期(天) */
    private Integer leadTime;

    /** 产品状态 */
    private String productStatus;

    /** 当前BOM ID */
    private Long currentBomId;

    /** 当前工艺路线ID */
    private Long currentRouteId;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private String createTime;

    /** 更新时间 */
    private String updateTime;
    /**
     * 批注
     */
    private String approveRemark;
    /** 单位 */
    private String unit;
}
