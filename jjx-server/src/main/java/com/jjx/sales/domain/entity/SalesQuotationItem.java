package com.jjx.sales.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 报价单明细
 */
@Data
@TableName("sales_quotation_item")
public class SalesQuotationItem {

    @TableId(type = IdType.AUTO)
    private Long itemId;

    /** 报价单ID */
    private Long quotationId;

    /** 产品ID */
    private Long productId;

    /** 产品编码 */
    private String productCode;

    /** 产品名称 */
    private String productName;

    /** 按键数量 */
    private Integer keyCount;

    /** 宽度mm */
    private BigDecimal width;

    /** 高度mm */
    private BigDecimal height;

    /** 厚度mm */
    private BigDecimal thickness;

    /** 材料类型 */
    private String materialType;

    /** 颜色 */
    private String color;

    /** 线路类型 */
    private String circuitType;

    /** 编码流水号（DEV-1108：样品报价编码生成器结构参数落库） */
    private String serialNo;

    /** 面板结构类型（DEV-1108） */
    private String panelType;

    /** 面板特征（DEV-1108） */
    private String panelFeature;

    /** 线路特征（DEV-1108） */
    private String circuitFeature;

    /** 连接器类型 */
    private String connectorType;

    /** 数量 */
    private Integer quantity;

    /** 单价 */
    private BigDecimal unitPrice;

    /** 单位 */
    private String unit;

    /** 金额 */
    private BigDecimal amount;

    /** 交期天数 */
    private Integer deliveryDays;

    /** 预计交期 */
    private LocalDate estimatedDeliveryDate;

    /** 自定义要求 */
    private String customRequirements;

    /** Logo要求 */
    private String logoRequirement;

    /** 认证要求 */
    private String certificationRequirement;

    /** 排序 */
    private Integer itemOrder;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
