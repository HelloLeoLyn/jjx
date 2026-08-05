package com.jjx.sales.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.jjx.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 销售报价单实体类
 * 薄膜开关ERP系统的销售报价单核心实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sales_quotation")
public class SalesQuotation extends BaseEntity {

    /**
     * 报价单ID
     */
    @TableId(type = IdType.AUTO)
    private Long quotationId;

    /** 链路追踪ID */
    private String traceId;

    /** 来源询价单号（非表字段，查询时按 traceId 关联填充） */
    @TableField(exist = false)
    private String sourceInquiryNo;

    /** 查询参数：来源询价单号（非表字段，按 traceId 子查询过滤） */
    @TableField(exist = false)
    private String inquiryNo;

    /**
     * 报价单编号
     */
    private String quotationNo;

    /** 报价单类型: 1标准品 2样品 */
    private Integer quotationType;

    /**
     * 客户ID
     */
    private Long customerId;

    /**
     * 客户名称
     */
    private String customerName;

    /**
     * 联系人
     */
    private String contactPerson;

    /**
     * 联系电话
     */
    private String contactPhone;

    /**
     * 报价日期
     */
    private LocalDate quotationDate;

    /**
     * 有效期至
     */
    private LocalDate validUntil;

    /**
     * 币种
     */
    private String currency;

    /**
     * 汇率
     */
    private BigDecimal exchangeRate;

    /**
     * 报价状态 (draft: 草稿, sent: 已发送, accepted: 已接受, rejected: 已拒绝, expired: 已过期)
     */
    /** 状态: 0草稿/1已发送/2已确认/3已拒绝/4已过期/5待审核/6已审核 */
    private Integer quotationStatus;

    /**
     * 小计金额
     */
    private BigDecimal subtotalAmount;

    /**
     * 税率
     */
    private BigDecimal taxRate;

    /**
     * 税额
     */
    private BigDecimal taxAmount;

    /**
     * 总金额
     */
    private BigDecimal totalAmount;

    /**
     * 折扣金额
     */
    private BigDecimal discountAmount;

    /**
     * 最终金额
     */
    private BigDecimal finalAmount;

    /**
     * 备注
     */
    private String remark;

    /**
     * 销售员ID
     */
    private Long salesPersonId;

    /**
     * 销售员姓名
     */
    private String salesPersonName;

    /**
     * 审核人ID
     */
    private Long approverId;

    /**
     * 审核人姓名
     */
    private String approverName;

    /**
     * 审核时间
     */
    private LocalDateTime approveTime;

    /**
     * 审核备注
     */
    private String approveRemark;

    /**
     * 发送时间
     */
    private LocalDateTime sendTime;

    /**
     * 发送方式
     */
    private String sendMethod;

    /**
     * 发送备注
     */
    private String sendRemark;

    /**
     * 转为订单ID
     */
    private Long convertedOrderId;

    /**
     * 转为订单时间
     */
    private LocalDateTime convertTime;

    /** 报价单明细（非表字段，保存/查询时处理） */
    @TableField(exist = false)
    private List<SalesQuotationItem> items;

    /**
     * 删除标志 (0: 正常, 1: 删除)
     */
    @TableLogic
    private Integer deleted;
}
