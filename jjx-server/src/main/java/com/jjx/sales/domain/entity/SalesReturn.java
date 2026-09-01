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
 * 销售退货单实体类
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sales_return")
public class SalesReturn extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long returnId;

    /** 退货单号 */
    private String returnNo;

    /** 订单ID */
    private Long orderId;

    /** 发货单ID */
    private Long deliveryId;

    /** 客户ID */
    private Long customerId;

    /** 客户名称 */
    private String customerName;

    /** 退货日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date returnDate;

    /** 退货原因 */
    private String returnReason;

    /** 退货类型：1质量问题 2规格不符 3数量错误 4客户取消 5其他 */
    private Integer returnType;

    /** 退货状态：1申请中 2已审核 3已收货 4已退款 5已完成 6已取消 */
    private Integer returnStatus;

    /** 总数量 */
    private Integer totalQuantity;

    /** 总金额 */
    private BigDecimal totalAmount;

    /** 退款金额 */
    private BigDecimal refundAmount;

    /** 备注 */
    private String remark;

    /** 审核人ID */
    private Long approverId;

    /** 审核人姓名 */
    private String approverName;

    /** 审核时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date approveTime;

    /** 审核备注 */
    private String approveRemark;

    /** 收货时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date receiveTime;

    /** 收货人ID */
    private Long receiveBy;

    /** 收货人姓名 */
    private String receiveName;

    /** 退款时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date refundTime;

    /** 退款人ID */
    private Long refundBy;

    /** 退款人姓名 */
    private String refundName;

    /** 逻辑删除 */
    @TableLogic
    private Integer deleted;
}
