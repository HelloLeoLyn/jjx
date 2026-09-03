package com.jjx.sales.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 销售工作台真实聚合查询（dev-20260901-087 任务1275）
 * 全部按当前销售归属过滤：询价/报价用 sales_person_id，订单/收款/发货 join 订单用 sales_manager_id
 */
@Mapper
public interface SalesWorkbenchMapper {

    // ========== 待办（我的） ==========

    /** 待处理询价：草稿(0)/待处理(1) */
    @Select("SELECT COUNT(*) FROM sales_inquiry WHERE deleted = 0 AND sales_person_id = #{userId} AND inquiry_status IN (0, 1)")
    Long countInquiryPending(@Param("userId") Long userId);

    /** 已发送未回复报价：已发送(1) */
    @Select("SELECT COUNT(*) FROM sales_quotation WHERE deleted = 0 AND sales_person_id = #{userId} AND quotation_status = 1")
    Long countQuotationSent(@Param("userId") Long userId);

    /** 卡审核报价：待审核(5) */
    @Select("SELECT COUNT(*) FROM sales_quotation WHERE deleted = 0 AND sales_person_id = #{userId} AND quotation_status = 5")
    Long countQuotationReviewing(@Param("userId") Long userId);

    /** 卡审核订单：待审核(2)/审核中(3)，标准订单 */
    @Select("SELECT COUNT(*) FROM sales_order WHERE deleted = 0 AND sales_manager_id = #{userId} AND sample_status IS NULL AND order_status IN (2, 3)")
    Long countOrderReviewing(@Param("userId") Long userId);

    /** 待转生产订单：已确认(6)未开始生产，标准订单 */
    @Select("SELECT COUNT(*) FROM sales_order WHERE deleted = 0 AND sales_manager_id = #{userId} AND sample_status IS NULL AND order_status = 6")
    Long countOrderReadyProduction(@Param("userId") Long userId);

    /** 已发货未签收：发货单状态 已发货(2)/运输中(3) */
    @Select("SELECT COUNT(*) FROM sales_delivery d JOIN sales_order o ON d.order_id = o.order_id " +
            "WHERE d.deleted = 0 AND o.deleted = 0 AND o.sales_manager_id = #{userId} AND d.delivery_status IN (2, 3)")
    Long countDeliveryUnreceived(@Param("userId") Long userId);

    /** 应收未清：已确认后(6-9)仍未结清（无到期日字段，逾期/临期精确计算后置） */
    @Select("SELECT COUNT(*) FROM sales_order WHERE deleted = 0 AND sales_manager_id = #{userId} " +
            "AND sample_status IS NULL AND order_status IN (6, 7, 8, 9) AND unpaid_amount > 0")
    Long countReceivableUnpaid(@Param("userId") Long userId);

    // ========== 本月业绩 ==========

    /** 本月报价额：quotation_date 本月，生效口径 已发送(1)/已确认(2)/已审核(6)/已完成(9)，不含拒绝/过期/草稿/待审核 */
    @Select("SELECT COALESCE(SUM(final_amount), 0) FROM sales_quotation WHERE deleted = 0 AND sales_person_id = #{userId} " +
            "AND quotation_date BETWEEN #{start} AND #{end} AND quotation_status IN (1, 2, 6, 9)")
    BigDecimal sumMonthQuotation(@Param("userId") Long userId, @Param("start") LocalDate start, @Param("end") LocalDate end);

    /** 本月订单额：order_date 本月，确认口径 已确认(6)/生产中(7)/已发货(8)/已完成(9)，标准订单 */
    @Select("SELECT COALESCE(SUM(final_amount), 0) FROM sales_order WHERE deleted = 0 AND sales_manager_id = #{userId} " +
            "AND sample_status IS NULL AND order_date BETWEEN #{start} AND #{end} AND order_status IN (6, 7, 8, 9)")
    BigDecimal sumMonthOrder(@Param("userId") Long userId, @Param("start") LocalDate start, @Param("end") LocalDate end);

    /** 本月回款额：收款单 receipt_date 本月，按订单归属过滤 */
    @Select("SELECT COALESCE(SUM(r.actual_amount), 0) FROM sales_receipt r JOIN sales_order o ON r.order_id = o.order_id " +
            "WHERE r.deleted = 0 AND o.deleted = 0 AND o.sales_manager_id = #{userId} " +
            "AND r.receipt_date BETWEEN #{start} AND #{end}")
    BigDecimal sumMonthReceipt(@Param("userId") Long userId, @Param("start") LocalDate start, @Param("end") LocalDate end);

    /** 本月新增客户：按 create_by（当前用户名）归属 */
    @Select("SELECT COUNT(*) FROM sales_customer WHERE deleted = 0 AND create_by = #{username} " +
            "AND create_time >= #{startTime} AND create_time < #{endTime}")
    Long countMonthNewCustomer(@Param("username") String username,
                               @Param("startTime") java.time.LocalDateTime startTime,
                               @Param("endTime") java.time.LocalDateTime endTime);

    /** 本月打样单数：样品单（sample_status 非空） */
    @Select("SELECT COUNT(*) FROM sales_order WHERE deleted = 0 AND sales_manager_id = #{userId} " +
            "AND sample_status IS NOT NULL AND order_date BETWEEN #{start} AND #{end}")
    Long countMonthSample(@Param("userId") Long userId, @Param("start") LocalDate start, @Param("end") LocalDate end);
}
