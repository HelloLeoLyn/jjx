package com.jjx.sales.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.jjx.sales.domain.entity.SalesDeliveryItem;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 销售发货单响应VO
 */
@Data
@Schema(description = "销售发货单响应VO")
public class SalesDeliveryVO {

    @Schema(description = "本次发货明细（分批发货）")
    private List<SalesDeliveryItem> items;
    @Schema(description = "拒收原因")
    private String rejectReason;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "拒收登记时间")
    private Date rejectTime;

    @Schema(description = "拒收登记人ID")
    private Long rejectBy;

    @Schema(description = "拒收登记人")
    private String rejectName;


    @Schema(description = "发货单ID")
    private Long deliveryId;

    @Schema(description = "发货单号")
    private String deliveryNo;

    @Schema(description = "销售订单ID")
    private Long orderId;

    @Schema(description = "客户ID")
    private Long customerId;

    @Schema(description = "客户名称")
    private String customerName;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @Schema(description = "发货日期")
    private Date deliveryDate;

    @Schema(description = "发货地址")
    private String deliveryAddress;

    @Schema(description = "联系人")
    private String contactPerson;

    @Schema(description = "联系电话")
    private String contactPhone;

    @Schema(description = "发货方式")
    private String deliveryMethod;

    @Schema(description = "物流单号")
    private String trackingNo;

    @Schema(description = "承运商")
    private String carrier;

    @Schema(description = "发货状态：1待发货 2已发货 4已签收 5已拒收（3运输中已弃用）")
    private Integer deliveryStatus;

    @Schema(description = "发货状态描述")
    private String deliveryStatusDesc;

    @Schema(description = "总数量")
    private Integer totalQuantity;

    @Schema(description = "总重量")
    private BigDecimal totalWeight;

    @Schema(description = "运费")
    private BigDecimal freightAmount;

    @Schema(description = "保价费")
    private BigDecimal insuranceAmount;

    @Schema(description = "其他费用")
    private BigDecimal otherCharges;

    @Schema(description = "总金额")
    private BigDecimal totalAmount;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "发货人姓名")
    private String deliveryPersonName;

    @Schema(description = "收货人")
    private String receiverName;

    @Schema(description = "收货人电话")
    private String receiverPhone;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "收货时间（系统登记）")
    private Date receiveTime;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @Schema(description = "客户签收日期（纸面）")
    private Date customerReceiveDate;

    @Schema(description = "收货备注")
    private String receiveRemark;

    @Schema(description = "打印次数")
    private Integer printCount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Schema(description = "最近打印时间")
    private Date lastPrintTime;

    @Schema(description = "最近打印人")
    private String lastPrintBy;
}
