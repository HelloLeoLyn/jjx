package com.jjx.sales.service;

import com.jjx.sales.domain.entity.OrderReviewRecord;
import com.jjx.sales.domain.entity.SalesOrder;

import java.util.List;

/**
 * 订单审核服务接口
 * 提供订单审核流程的业务逻辑操作
 */
public interface IOrderReviewService {

    /**
     * 获取订单审核记录列表
     *
     * @param orderId 订单ID
     * @return 审核记录列表
     */
    List<com.jjx.sales.domain.vo.OrderReviewProcessVO> getOrderReviewRecords(Long orderId);

    /**
     * 获取待审核订单列表
     *
     * @param reviewerId 审核人ID
     * @return 待审核订单列表
     */
    List<SalesOrder> getPendingReviewOrders(Long reviewerId);

    /**
     * 获取已提交审核订单列表
     *
     * @param submitterId 提交人ID
     * @return 已提交审核订单列表
     */
    List<SalesOrder> getSubmittedReviewOrders(Long submitterId);

    /**
     * 获取审核统计信息
     *
     * @param reviewerId 审核人ID
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 审核统计信息
     */
    Object getReviewStatistics(Long reviewerId, String startDate, String endDate);

    /**
     * 检查订单是否可提交审核
     *
     * @param orderId 订单ID
     * @return 是否可提交审核
     */
    boolean canSubmitForReview(Long orderId);

    /**
     * 检查订单是否可审核
     *
     * @param orderId 订单ID
     * @param reviewerId 审核人ID
     * @return 是否可审核
     */
    boolean canReviewOrder(Long orderId, Long reviewerId);

    /**
     * 检查订单是否可客户确认
     *
     * @param orderId 订单ID
     * @param customerId 客户ID
     * @return 是否可客户确认
     */
    boolean canConfirmByCustomer(Long orderId, Long customerId);

    /**
     * 获取订单审核进度
     *
     * @param orderId 订单ID
     * @return 审核进度信息
     */
    Object getReviewProgress(Long orderId);

    /**
     * 获取审核超时订单列表
     *
     * @param timeoutHours 超时小时数
     * @return 超时订单列表
     */
    List<SalesOrder> getTimeoutReviewOrders(Integer timeoutHours);

    /**
     * 发送审核提醒
     *
     * @param orderId 订单ID
     * @param reviewerId 审核人ID
     * @param reminderType 提醒类型
     * @return 是否发送成功
     */
    boolean sendReviewReminder(Long orderId, Long reviewerId, String reminderType);

    /**
     * 导出审核记录
     *
     * @param orderId 订单ID
     * @return 导出文件路径
     */
    String exportReviewRecords(Long orderId);

    /**
     * 获取多级审核配置
     *
     * @param orderType 订单类型
     * @return 审核配置信息
     */
    Object getMultiLevelReviewConfig(String orderType);

    /**
     * 设置多级审核配置
     *
     * @param orderType 订单类型
     * @param config 审核配置
     * @return 是否设置成功
     */
    boolean setMultiLevelReviewConfig(String orderType, Object config);

    /**
     * 获取审核流程图
     *
     * @param orderId 订单ID
     * @return 审核流程图数据
     */
    Object getReviewFlowChart(Long orderId);

    /**
     * 获取审核权限检查
     *
     * @param orderId 订单ID
     * @param userId 用户ID
     * @return 审核权限信息
     */
    Object getReviewPermissions(Long orderId, Long userId);

}
