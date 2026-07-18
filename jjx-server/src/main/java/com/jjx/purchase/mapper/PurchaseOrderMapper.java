package com.jjx.purchase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jjx.purchase.domain.entity.PurchaseOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 采购订单Mapper接口
 * 提供采购订单的数据访问操作
 */
@Mapper
public interface PurchaseOrderMapper extends BaseMapper<PurchaseOrder> {

    /**
     * 检查订单号是否存在
     *
     * @param orderNo 订单号
     * @return 是否存在
     */
    @Select("SELECT COUNT(*) FROM purchase_order WHERE order_no = #{orderNo}")
    int checkOrderNoUnique(@Param("orderNo") String orderNo);

    /**
     * 更新订单审批状态
     *
     * @param orderId 订单ID
     * @param approvalStatus 审批状态
     * @return 结果
     */
    @Update("UPDATE purchase_order SET approval_status = #{approvalStatus}, update_time = NOW() WHERE order_id = #{orderId}")
    int updateApprovalStatus(@Param("orderId") Long orderId, @Param("approvalStatus") Integer approvalStatus);

    /**
     * 更新审批信息
     *
     * @param orderId 订单ID
     * @param approverId 审批人ID
     * @param approverName 审批人姓名
     * @param approvalComment 审批意见
     * @param approvalStatus 审批状态
     * @return 结果
     */
    @Update("UPDATE purchase_order SET approver_id = #{approverId}, approver_name = #{approverName}, approval_time = NOW(), approval_comment = #{approvalComment}, approval_status = #{approvalStatus}, update_time = NOW() WHERE order_id = #{orderId}")
    int updateApprovalInfo(@Param("orderId") Long orderId,
                          @Param("approverId") Long approverId,
                          @Param("approverName") String approverName,
                          @Param("approvalComment") String approvalComment,
                          @Param("approvalStatus") Integer approvalStatus);

    /**
     * 更新收货状态
     *
     * @param orderId 订单ID
     * @param receiptStatus 收货状态
     * @return 结果
     */
    @Update("UPDATE purchase_order SET receipt_status = #{receiptStatus}, update_time = NOW() WHERE order_id = #{orderId}")
    int updateReceiptStatus(@Param("orderId") Long orderId, @Param("receiptStatus") Integer receiptStatus);

    /**
     * 更新付款信息
     *
     * @param orderId 订单ID
     * @param paidAmount 已付款金额
     * @param paymentStatus 付款状态
     * @return 结果
     */
    @Update("UPDATE purchase_order SET paid_amount = paid_amount + #{paidAmount}, payment_status = #{paymentStatus}, update_time = NOW() WHERE order_id = #{orderId}")
    int updatePaymentInfo(@Param("orderId") Long orderId,
                         @Param("paidAmount") BigDecimal paidAmount,
                         @Param("paymentStatus") Integer paymentStatus);

    /**
     * 更新实际交货日期
     *
     * @param orderId 订单ID
     * @param actualDeliveryDate 实际交货日期
     * @return 结果
     */
    @Update("UPDATE purchase_order SET actual_delivery_date = #{actualDeliveryDate}, update_time = NOW() WHERE order_id = #{orderId}")
    int updateActualDeliveryDate(@Param("orderId") Long orderId, @Param("actualDeliveryDate") LocalDate actualDeliveryDate);

    /**
     * 根据供应商ID查询订单列表
     *
     * @param supplierId 供应商ID
     * @return 订单列表
     */
    @Select("SELECT * FROM purchase_order WHERE supplier_id = #{supplierId} ORDER BY order_date DESC")
    List<PurchaseOrder> selectOrdersBySupplierId(@Param("supplierId") Long supplierId);

    /**
     * 根据审批状态查询订单列表
     *
     * @param approvalStatus 审批状态
     * @return 订单列表
     */
    @Select("SELECT * FROM purchase_order WHERE approval_status = #{approvalStatus} ORDER BY order_date DESC")
    List<PurchaseOrder> selectOrdersByStatus(@Param("approvalStatus") Integer approvalStatus);

    /**
     * 查询待审批的订单列表（状态为待审批）
     */
    @Select("SELECT * FROM purchase_order WHERE approval_status = 3 ORDER BY order_date DESC")
    List<PurchaseOrder> selectPendingApprovalOrders();

    /**
     * 查询待收货的订单列表（已批准或执行中的订单）
     */
    @Select("SELECT * FROM purchase_order WHERE receipt_status IN (0, 1) AND approval_status IN (3, 4) ORDER BY expected_delivery_date ASC")
    List<PurchaseOrder> selectPendingReceiptOrders();

    /**
     * 查询待付款的订单列表
     */
    @Select("SELECT * FROM purchase_order WHERE payment_status IN (0, 1) AND approval_status IN (4) ORDER BY order_date DESC")
    List<PurchaseOrder> selectPendingPaymentOrders();

    /**
     * 查询紧急订单列表
     */
    @Select("SELECT * FROM purchase_order WHERE urgent_flag = 1 AND approval_status NOT IN (2) ORDER BY expected_delivery_date ASC")
    List<PurchaseOrder> selectUrgentOrders();

    /**
     * 根据日期范围查询订单
     *
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 订单列表
     */
    @Select("SELECT * FROM purchase_order WHERE order_date BETWEEN #{startDate} AND #{endDate} ORDER BY order_date DESC")
    List<PurchaseOrder> selectOrdersByDateRange(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}
