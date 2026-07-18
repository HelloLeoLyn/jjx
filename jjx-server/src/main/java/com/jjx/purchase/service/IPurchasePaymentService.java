package com.jjx.purchase.service;

import com.jjx.purchase.domain.dto.PurchasePaymentDTO;
import com.jjx.purchase.domain.entity.PurchasePayment;

import java.util.List;
import java.util.Map;

/**
 * 采购付款Service接口
 */
public interface IPurchasePaymentService {

    /**
     * 查询付款列表
     */
    List<PurchasePayment> selectPaymentList(PurchasePaymentDTO dto);

    /**
     * 查询付款详情
     */
    PurchasePayment selectPaymentById(Long paymentId);

    /**
     * 新增付款
     */
    int insertPayment(PurchasePaymentDTO dto);

    /**
     * 修改付款
     */
    int updatePayment(PurchasePaymentDTO dto);

    /**
     * 删除付款
     */
    int deletePaymentById(Long paymentId);

    /**
     * 批量删除付款
     */
    int deletePaymentByIds(Long[] paymentIds);

    /**
     * 审批付款
     */
    int approvePayment(Long paymentId, String approvalStatus, String approverName, String approvalComment);

    /**
     * 确认付款
     */
    int confirmPayment(PurchasePaymentDTO dto);

    /**
     * 检查付款单号是否唯一
     */
    boolean checkPaymentNoUnique(String paymentNo);

    /**
     * 生成付款单号
     */
    String generatePaymentNo();

    /**
     * 根据订单ID查询付款记录
     */
    List<PurchasePayment> selectByOrderId(Long orderId);

    /**
     * 根据供应商ID查询付款记录
     */
    List<PurchasePayment> selectBySupplierId(Long supplierId);

    /**
     * 查询待审批的付款列表
     */
    List<PurchasePayment> selectPendingApproval();

    /**
     * 查询已审批的付款列表
     */
    List<PurchasePayment> selectApproved();

    /**
     * 查询今日付款
     */
    List<PurchasePayment> selectToday();

    /**
     * 查询本周付款
     */
    List<PurchasePayment> selectWeek();

    /**
     * 查询本月付款
     */
    List<PurchasePayment> selectMonth();

    /**
     * 获取付款统计信息
     */
    Map<String, Object> getPaymentStatistics();

    /**
     * 导出付款列表
     */
    String exportPaymentList(PurchasePaymentDTO dto);
}
