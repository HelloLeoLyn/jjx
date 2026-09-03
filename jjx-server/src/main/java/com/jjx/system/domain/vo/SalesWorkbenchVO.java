package com.jjx.system.domain.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 销售成员工作台（dev-20260901-087 任务1275）
 * 全部按当前销售过滤 = 我的工作台
 */
@Data
public class SalesWorkbenchVO {

    // ===== 待办提醒 =====
    /** 待处理询价 */
    private Long inquiryPending = 0L;
    /** 已发送未回复报价 */
    private Long quotationSent = 0L;
    /** 卡审核报价（待审核） */
    private Long quotationReviewing = 0L;
    /** 卡审核订单（待审核/审核中） */
    private Long orderReviewing = 0L;
    /** 待转生产订单（已确认未投产） */
    private Long orderReadyProduction = 0L;
    /** 已发货未签收 */
    private Long deliveryUnreceived = 0L;
    /** 应收未清（已确认后未结清） */
    private Long receivableUnpaid = 0L;

    // ===== 本月业绩 =====
    /** 本月报价额 */
    private BigDecimal monthQuotationAmount = BigDecimal.ZERO;
    /** 本月订单额 */
    private BigDecimal monthOrderAmount = BigDecimal.ZERO;
    /** 本月回款额 */
    private BigDecimal monthReceiptAmount = BigDecimal.ZERO;
    /** 本月新增客户 */
    private Long monthNewCustomerCount = 0L;
    /** 本月打样单数 */
    private Long monthSampleCount = 0L;
}
