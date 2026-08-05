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
import java.time.LocalDateTime;

/**
 * 销售订单实体类
 * 薄膜开关ERP系统的销售订单核心实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sales_order")
public class SalesOrder extends BaseEntity {
    /**
     * 订单ID
     */
    @TableId(type = IdType.AUTO)
    private Long orderId;

    /** 链路追踪ID */
    private String traceId;

    /**
     * 订单编号
     */
    private String orderNo;

    /**
     * 报价单ID
     */
    private Long quotationId;

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
     * 订单日期
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date orderDate;

    /**
     * 客户要求交货日期
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date deliveryDate;

    /**
     * 订单类型: 1标准订单,2样品订单
     */
    private Integer orderType;

    /**
     * 订单状态: 1草稿,2已确认,3部分发货,4已发货,5部分完成,6已完成,7已取消
     */
    private Integer orderStatus;

    /**
     * 样品单状态: 1=已创建,2=待审核,3=工程打样中,4=样品待送样,5=已送样待确认,6=样品确认,7=已转量产,8=已关闭,9=客户退回,10=已取消
     */
    private Integer sampleStatus;

    /**
     * 样品迭代轮次
     */
    private Integer sampleRound;

    /**
     * 打样数量
     */
    private Integer sampleQty;

    /**
     * 工程备注
     */
    private String engineeringNote;

    /**
     * 工程接单人
     */
    private String engineeringAcceptor;

    /**
     * 工程接单时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date engineeringAcceptTime;

    /**
     * 工程拒单原因
     */
    private String rejectReason;

    /**
     * 打样当前工序
     */
    private String currentProcess;

    /**
     * 打样成本
     */
    private java.math.BigDecimal sampleCost;

    /**
     * 打样工时(小时)
     */
    private java.math.BigDecimal sampleWorkHours;

    /**
     * 送样快递单号
     */
    private String sampleTrackingNo;

    /**
     * 送样日期
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date sampleSendDate;

    /**
     * 客户确认日期
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date sampleConfirmDate;

    /** 客户确认人（DEV-343/314） */
    private String confirmBy;

    /** 确认方式 */
    private String confirmMethod;

    /** 确认时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime confirmTime;

    /**
     * 客户方确认人
     */
    private String sampleClientName;

    /**
     * 转量产后的标准订单ID
     */
    private Long convertedOrderId;

    /**
     * 转量产时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date convertOrderTime;

    /**
     * 生产状态: 1无生产,2部分生产中,3全部生产中,4生产完成
     */
    private Integer prodStatus;

    /**
     * 是否急单: 0否,1是
     */
    private Integer isUrgent;

    /**
     * 加急原因
     */
    private String urgentReason;

    /**
     * 币种
     */
    private String currency;

    /**
     * 汇率
     */
    private BigDecimal exchangeRate;

    /**
     * 付款条件
     */
    private String paymentTerms;

    /**
     * 交货条件
     */
    private String deliveryTerms;

    /**
     * 交货地址
     */
    private String deliveryAddress;

    /**
     * 总金额
     */
    private BigDecimal totalAmount;

    /**
     * 税率
     */
    private BigDecimal taxRate;

    /**
     * 税额
     */
    private BigDecimal taxAmount;

    /**
     * 含税总金额
     */
    private BigDecimal totalAmountWithTax;

    /**
     * 折扣率
     */
    private BigDecimal discountRate;

    /**
     * 折扣金额
     */
    private BigDecimal discountAmount;

    /**
     * 最终金额
     */
    private BigDecimal finalAmount;

    /**
     * 支付状态: 1未支付,2支付中,3已支付,4部分支付,5已退款
     */
    private Integer paymentStatus;

    /**
     * 已付金额
     */
    private BigDecimal paidAmount;

    /**
     * 未付金额
     */
    private BigDecimal unpaidAmount;

    /**
     * 总数量
     */
    private Integer totalQuantity;

    /**
     * 已发货数量
     */
    private Integer shippedQuantity;

    /**
     * 已生产数量
     */
    private Integer producedQuantity;

    /**
     * 销售负责人ID
     */
    private Long salesManagerId;

    /**
     * 销售负责人姓名
     */
    private String salesManagerName;

    /**
     * 备注
     */
    private String remark;

    /**
     * 删除标志 (0: 正常, 1: 删除)
     */
    @TableLogic
    private Integer deleted;
}
