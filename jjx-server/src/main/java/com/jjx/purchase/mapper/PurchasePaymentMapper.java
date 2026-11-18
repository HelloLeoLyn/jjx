package com.jjx.purchase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.purchase.domain.entity.PurchasePayment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 采购付款Mapper接口
 */
@Mapper
public interface PurchasePaymentMapper extends BaseMapper<PurchasePayment> {

    /** 金额只在数据库汇总；筛选与列表共用，分页不影响统计。应付按计划日，已付按实际日，驳回不计应付/逾期。 */
    @Select("""
        SELECT COUNT(*) AS "totalCount",
          COALESCE(SUM(CASE WHEN approval_status=#{pending} THEN 1 ELSE 0 END),0) AS "pendingCount",
          COALESCE(SUM(CASE WHEN approval_status=#{approved} THEN 1 ELSE 0 END),0) AS "approvedCount",
          COALESCE(SUM(CASE WHEN payment_status=#{paid} THEN 1 ELSE 0 END),0) AS "paidCount",
          COALESCE(SUM(payment_amount),0) AS "totalAmount",
          COALESCE(SUM(CASE WHEN (approval_status IS NULL OR approval_status != #{rejected})
            AND payment_date >= #{monthStart} AND payment_date < #{monthEnd} THEN payment_amount ELSE 0 END),0) AS "monthDueAmount",
          COALESCE(SUM(CASE WHEN payment_status=#{paid} AND actual_payment_date >= #{monthStart}
            AND actual_payment_date < #{monthEnd} THEN payment_amount ELSE 0 END),0) AS "monthPaidAmount",
          COALESCE(SUM(CASE WHEN (approval_status IS NULL OR approval_status != #{rejected})
            AND (payment_status IS NULL OR payment_status != #{paid}) AND payment_date < #{today}
            THEN payment_amount ELSE 0 END),0) AS "overdueAmount"
        FROM purchase_payment ${ew.customSqlSegment}
        """)
    java.util.Map<String, Object> selectStatistics(
        @Param("ew") com.baomidou.mybatisplus.core.conditions.Wrapper<PurchasePayment> query,
        @Param("today") java.time.LocalDate today, @Param("monthStart") java.time.LocalDate monthStart,
        @Param("monthEnd") java.time.LocalDate monthEnd, @Param("paid") Integer paid,
        @Param("pending") String pending, @Param("approved") String approved, @Param("rejected") String rejected);

    /**
     * 检查付款单号是否唯一
     */
    @Select("SELECT COUNT(*) FROM purchase_payment WHERE payment_no = #{paymentNo}")
    int checkPaymentNoUnique(@Param("paymentNo") String paymentNo);

    /**
     * 根据订单ID查询付款记录
     */
    @Select("SELECT * FROM purchase_payment WHERE order_id = #{orderId} ORDER BY payment_date DESC")
    List<PurchasePayment> selectByOrderId(@Param("orderId") Long orderId);

    /**
     * 根据供应商ID查询付款记录
     */
    @Select("SELECT pp.* FROM purchase_payment pp " +
            "LEFT JOIN purchase_order po ON pp.order_id = po.order_id " +
            "WHERE po.supplier_id = #{supplierId} ORDER BY pp.payment_date DESC")
    List<PurchasePayment> selectBySupplierId(@Param("supplierId") Long supplierId);

    /**
     * 查询待审批的付款列表
     */
    @Select("SELECT * FROM purchase_payment WHERE approval_status = 'PENDING' ORDER BY payment_date ASC")
    List<PurchasePayment> selectPendingApproval();

    /**
     * 查询已审批的付款列表
     */
    @Select("SELECT * FROM purchase_payment WHERE approval_status = 'APPROVED' ORDER BY payment_date DESC")
    List<PurchasePayment> selectApproved();

    /**
     * 查询今日付款
     */
    @Select("SELECT * FROM purchase_payment WHERE DATE(create_time) = CURDATE() ORDER BY create_time DESC")
    List<PurchasePayment> selectToday();

    /**
     * 查询本周付款
     */
    @Select("SELECT * FROM purchase_payment WHERE YEARWEEK(create_time, 1) = YEARWEEK(CURDATE(), 1) ORDER BY create_time DESC")
    List<PurchasePayment> selectWeek();

    /**
     * 查询本月付款
     */
    @Select("SELECT * FROM purchase_payment WHERE YEAR(create_time) = YEAR(CURDATE()) AND MONTH(create_time) = MONTH(CURDATE()) ORDER BY create_time DESC")
    List<PurchasePayment> selectMonth();
}
