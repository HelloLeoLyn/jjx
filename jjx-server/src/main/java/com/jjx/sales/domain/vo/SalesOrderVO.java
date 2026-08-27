package com.jjx.sales.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 销售订单响应VO
 */
@Data
@Schema(description = "销售订单响应VO")
public class SalesOrderVO {

    @Schema(description = "订单ID", example = "1001")
    private Long orderId;

    @Schema(description = "订单编号", example = "SO202604190001")
    private String orderNo;

    @Schema(description = "报价单ID", example = "1001")
    private Long quotationId;

    @Schema(description = "客户ID", example = "1001")
    private Long customerId;

    @Schema(description = "客户名称", example = "XX科技有限公司")
    private String customerName;

    @Schema(description = "联系人", example = "张三")
    private String contactPerson;

    @Schema(description = "联系电话", example = "13800138000")
    private String contactPhone;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @Schema(description = "订单日期", example = "2026-04-19")
    private Date orderDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @Schema(description = "客户要求交货日期", example = "2026-05-19")
    private Date deliveryDate;

    @Schema(description = "订单类型", example = "1")
    private Integer orderType;

    @Schema(description = "订单类型描述", example = "标准订单")
    private String orderTypeDesc;

    @Schema(description = "订单状态", example = "1")
    private Integer orderStatus;

    @Schema(description = "订单状态描述", example = "草稿")
    private String orderStatusDesc;

    @Schema(description = "生产状态", example = "1")
    private Integer prodStatus;

    @Schema(description = "生产状态描述", example = "无生产")
    private String prodStatusDesc;

    @Schema(description = "是否急单", example = "0")
    private Integer isUrgent;

    @Schema(description = "是否急单描述", example = "否")
    private String isUrgentDesc;

    @Schema(description = "加急原因", example = "客户项目紧急")
    private String urgentReason;

    @Schema(description = "币种", example = "CNY")
    private String currency;

    @Schema(description = "汇率", example = "1.0000")
    private BigDecimal exchangeRate;

    @Schema(description = "付款条件", example = "30%预付款，70%发货前付清")
    private String paymentTerms;

    @Schema(description = "交货条件", example = "FOB Shanghai")
    private String deliveryTerms;

    @Schema(description = "交货地址", example = "上海市浦东新区XX路XX号")
    private String deliveryAddress;

    @Schema(description = "总金额", example = "10000.00")
    private BigDecimal totalAmount;

    @Schema(description = "税率", example = "0.13")
    private BigDecimal taxRate;

    @Schema(description = "税额", example = "1300.00")
    private BigDecimal taxAmount;

    @Schema(description = "含税总金额", example = "11300.00")
    private BigDecimal totalAmountWithTax;

    @Schema(description = "折扣率", example = "0.05")
    private BigDecimal discountRate;

    @Schema(description = "折扣金额", example = "565.00")
    private BigDecimal discountAmount;

    @Schema(description = "最终金额", example = "10735.00")
    private BigDecimal finalAmount;

    @Schema(description = "支付状态", example = "1")
    private Integer paymentStatus;

    @Schema(description = "支付状态描述", example = "未支付")
    private String paymentStatusDesc;

    @Schema(description = "已付金额", example = "0.00")
    private BigDecimal paidAmount;

    @Schema(description = "未付金额", example = "10735.00")
    private BigDecimal unpaidAmount;

    @Schema(description = "总数量", example = "100")
    private Integer totalQuantity;

    @Schema(description = "已发货数量", example = "0")
    private Integer shippedQuantity;

    @Schema(description = "已生产数量", example = "0")
    private Integer producedQuantity;

    @Schema(description = "销售负责人ID", example = "1001")
    private Long salesManagerId;

    @Schema(description = "销售负责人姓名", example = "李四")
    private String salesManagerName;

    @Schema(description = "备注", example = "客户要求加急处理")
    private String remark;

    @Schema(description = "链路追踪ID", example = "af6d2e3034ab4649")
    private String traceId;

    @Schema(description = "创建时间", example = "2026-04-19 10:00:00")
    private Date createTime;

    @Schema(description = "创建人", example = "admin")
    private String createBy;

    @Schema(description = "更新时间", example = "2026-04-19 10:00:00")
    private Date updateTime;

    @Schema(description = "更新人", example = "admin")
    private String updateBy;

    @Schema(description = "产品明细", example = "[SalesOrderProductVO.class]")
    private List<SalesOrderProductVO> items;
}
