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
    @Select("SELECT * FROM purchase_payment WHERE payment_status = 'pending' ORDER BY payment_date ASC")
    List<PurchasePayment> selectPendingApproval();

    /**
     * 查询已审批的付款列表
     */
    @Select("SELECT * FROM purchase_payment WHERE payment_status = 'approved' ORDER BY payment_date DESC")
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
