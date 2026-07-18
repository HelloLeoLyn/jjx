package com.jjx.purchase.service;

import com.jjx.common.core.page.PageResult;
import com.jjx.purchase.domain.dto.POrderStatusDTO;
import com.jjx.purchase.domain.dto.PurchaseOrderApprovalDTO;
import com.jjx.purchase.domain.dto.PurchaseOrderDTO;
import com.jjx.purchase.domain.dto.PurchaseOrderQueryDTO;
import com.jjx.purchase.domain.dto.PurchaseOrderReceiveDTO;
import com.jjx.purchase.domain.vo.PurchaseOrderItemVO;
import com.jjx.purchase.domain.vo.PurchaseOrderVO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 采购订单服务接口
 * 提供采购订单的业务逻辑操作
 */
public interface IPurchaseOrderService {

    /**
     * 查询采购订单列表
     *
     * @param queryDTO 采购订单查询条件
     * @return 采购订单列表
     */
    PageResult<PurchaseOrderVO> page(PurchaseOrderQueryDTO queryDTO);

    /**
     * 根据ID查询采购订单
     *
     * @param orderId 订单ID
     * @return 采购订单
     */
    PurchaseOrderVO selectOrderById(Long orderId);

    /**
     * 查询订单明细列表
     *
     * @param orderId 订单ID
     * @return 订单明细列表
     */
    List<PurchaseOrderItemVO> selectOrderItemList(Long orderId);

    /**
     * 新增采购订单
     *
     * @param orderDTO 采购订单数据传输对象
     * @return 结果
     */
    int insertOrder(PurchaseOrderDTO orderDTO);

    /**
     * 修改采购订单
     *
     * @param orderDTO 采购订单数据传输对象
     * @return 结果
     */
    int updateOrder(PurchaseOrderDTO orderDTO);

    /**
     * 检查订单号是否存在
     *
     * @param orderNo 订单号
     * @return 是否存在
     */
    boolean checkOrderNoUnique(String orderNo);

    /**
     * 更新订单审批状态
     *
     * @param orderId 订单ID
     * @param approvalStatus 审批状态
     * @return 结果
     */
    int updateOrderStatus(Long orderId, Integer approvalStatus);

    /**
     * 提交订单审批
     *
     * @param orderId 订单ID
     * @return 结果
     */
    int submitOrder(Long orderId);

    /**
     * 批量提交订单审批
     *
     * @param orderIds 订单ID列表
     * @return 结果
     */
    int batchSubmitOrders(List<Long> orderIds);

    /**
     * 审批订单
     *
     */
    int approveOrder(PurchaseOrderApprovalDTO dto);

    /**
     * 更新收货状态
     *
     * @param orderId 订单ID
     * @param receiptStatus 收货状态
     * @return 结果
     */
    int updateReceiptStatus(Long orderId, Integer receiptStatus);

    /**
     * 收货操作（单条明细）
     *
     * @param orderId 订单ID
     * @param itemId 明细ID
     * @param receivedQuantity 收货数量
     * @param inspectionResult 检验结果
     * @param inspectionRemark 检验备注
     * @return 结果
     */
    int receiveOrderItem(Long orderId, Long itemId, BigDecimal receivedQuantity, String inspectionResult, String inspectionRemark);

    /**
     * 批量收货操作
     *
     * @param dto 收货数据传输对象，包含订单ID和收货明细列表
     * @return 结果
     */
    int batchReceiveOrderItems(PurchaseOrderReceiveDTO dto);

    /**
     * 更新付款信息
     *
     * @param orderId 订单ID
     * @param paidAmount 已付款金额
     * @param paymentStatus 付款状态
     * @return 结果
     */
    int updatePaymentInfo(Long orderId, BigDecimal paidAmount, Integer paymentStatus);

    /**
     * 更新实际交货日期
     *
     * @param orderId 订单ID
     * @param actualDeliveryDate 实际交货日期
     * @return 结果
     */
    int updateActualDeliveryDate(Long orderId, LocalDate actualDeliveryDate);

    /**
     * 根据供应商ID查询订单列表
     *
     * @param supplierId 供应商ID
     * @return 订单列表
     */
    List<PurchaseOrderVO> selectOrdersBySupplierId(Long supplierId);

    /**
     * 根据审批状态查询订单列表
     *
     * @param approvalStatus 审批状态
     * @return 订单列表
     */
    List<PurchaseOrderVO> selectOrdersByStatus(Integer approvalStatus);



    /**
     * 查询待收货的订单列表
     *
     * @return 待收货订单列表
     */
    List<PurchaseOrderVO> selectPendingReceiptOrders();

    /**
     * 查询待付款的订单列表
     *
     * @return 待付款订单列表
     */
    List<PurchaseOrderVO> selectPendingPaymentOrders();


    /**
     * 根据日期范围查询订单
     *
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 订单列表
     */
    List<PurchaseOrderVO> selectOrdersByDateRange(LocalDate startDate, LocalDate endDate);

    /**
     * 导出订单列表
     *
     * @param queryDTO 查询条件
     * @return 导出文件路径
     */
    String exportOrderList(PurchaseOrderQueryDTO queryDTO);

    /**
     * 导出订单详情
     *
     * @param orderId 订单ID
     * @return 导出文件路径
     */
    String exportOrderDetail(Long orderId);

    /**
     * 获取订单统计信息
     *
     * @return 统计信息
     */
    Object getOrderStatistics();

    /**
     * 生成采购订单号
     *
     * @return 采购订单号
     */
    String generateOrderNo();

    /**
     * 复制订单
     *
     * @param sourceOrderId 源订单ID
     * @return 新订单ID
     */
    Long copyOrder(Long sourceOrderId);

    /**
     * 根据订单id查询订单详情项
     */
    List<PurchaseOrderItemVO> selectOrderItemsById(Long orderId);

    /**
     * 取消订单
     */
    void cancelOrder(Long orderId);

    /**
     * 修改订单状态
     */
    void updateOrderStatus(POrderStatusDTO dto);
}
