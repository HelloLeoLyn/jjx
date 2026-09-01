package com.jjx.sales.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.jjx.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 销售发货单实体类
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sales_delivery")
public class SalesDelivery extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long deliveryId;

    /** 发货单号 */
    private String deliveryNo;

    /** 销售订单ID */
    private Long orderId;

    /** 客户ID */
    private Long customerId;

    /** 客户名称 */
    private String customerName;

    /** 发货日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date deliveryDate;

    /** 发货地址 */
    private String deliveryAddress;

    /** 联系人 */
    private String contactPerson;

    /** 联系电话 */
    private String contactPhone;

    /** 发货方式 */
    private String deliveryMethod;

    /** 物流单号 */
    private String trackingNo;

    /** 承运商 */
    private String carrier;

    /** 发货状态：1待发货 2已发货 3运输中 4已签收 5已拒收 */
    private Integer deliveryStatus;

    /** 总数量 */
    private Integer totalQuantity;

    /** 总重量 */
    private BigDecimal totalWeight;

    /** 总体积 */
    private BigDecimal totalVolume;

    /** 运费 */
    private BigDecimal freightAmount;

    /** 保价费 */
    private BigDecimal insuranceAmount;

    /** 其他费用 */
    private BigDecimal otherCharges;

    /** 总金额 */
    private BigDecimal totalAmount;

    /** 备注 */
    private String remark;

    /** 发货人ID */
    private Long deliveryPersonId;

    /** 发货人姓名 */
    private String deliveryPersonName;

    /** 收货人 */
    private String receiverName;

    /** 收货人电话 */
    private String receiverPhone;

    /** 收货时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date receiveTime;

    /** 收货备注 */
    private String receiveRemark;

    /** 签收操作人ID */
    private Long receiveBy;

    /** 签收操作人姓名 */
    private String receiveName;

    /** 逻辑删除 */
    @TableLogic
    private Integer deleted;
}
