package com.jjx.product.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 产品主表实体类
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("product")
@JsonIgnoreProperties(ignoreUnknown = true)
public class Product {

    /**
     * 产品ID
     */
    @TableId(type = IdType.AUTO)
    private Long productId;

    /**
     * 产品编码
     */
    private String productCode;

    /**
     * 产品名称
     */
    private String productName;

    /**
     * 分类ID
     */
    private Long categoryId;

    /**
     * 客户ID（2026-08-10 产品关联客户）
     */
    private Long customerId;

    /**
     * 客户名称（冗余，列表/详情直接显示）
     */
    private String customerName;

    /**
     * 来源标记（inquiry/quotation 建档草稿，草稿清理用）
     */
    private String fromSource;

    /**
     * 类型：standard标准/custom定制
     */
    private String productType;

    /**
     * 规格参数（JSON格式）
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String specJson;

    /**
     * 基础售价
     */
    private BigDecimal basePrice;

    /**
     * 标准成本
     */
    private BigDecimal costPrice;

    /**
     * 最小起订量
     */
    private Integer minOrderQty;

    /**
     * 标准交期(天)
     */
    private Integer leadTime;

    /**
     * 产品状态: 1开发中,2待审核,3审核中,4已通过,5已驳回,6已发布,7停产,8取消
     */
    private Integer productStatus;

    /**
     * 当前BOM ID
     */
    private Long currentBomId;

    /**
     * 当前工艺路线ID
     */
    private Long currentRouteId;

    /**
     * 当前BOM版本号
     */
    private String currentBomVersion;

    /**
     * 当前Routing版本号
     */
    private String currentRoutingVersion;

    /**
     * 创建者
     */
    @TableField(fill = FieldFill.INSERT)
    private String createBy;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /**
     * 更新者
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    /**
     * 批注
     */
    private String approveRemark;

    /**
     * 备注
     */
    private String remark;

    /** 单位 */
    private String unit;
}
