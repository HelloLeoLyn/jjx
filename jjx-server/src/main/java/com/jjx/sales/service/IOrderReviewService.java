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
     * 提交订单审核
     *
     * @param orderId 订单ID
     * @param submitterId 提交人ID
     * @param submitterName 提交人姓名
     * @param submitComment 提交备注
     * @return 审核记录ID
     */
    Long submitOrderForReview(Long orderId, Long submitterId, String submitterName, String submitComment);

    /**
     * 开始审核订单
     *
     * @param orderId 订单ID
     * @param reviewerId 审核人ID
     * @param reviewerName 审核人姓名
     * @param reviewerRole 审核人角色
     * @return 审核记录ID
     */
    Long startOrderReview(Long orderId, Long reviewerId, String reviewerName, String reviewerRole);

    /**
     * 审核通过订单
     *
     * @param orderId 订单ID
     * @param reviewerId 审核人ID
     * @param reviewerName 审核人姓名
     * @param reviewComment 审核意见
     * @param attachments 审核附件
     * @return 审核记录ID
     */
    Long approveOrder(Long orderId, Long reviewerId, String reviewerName, String reviewComment, String attachments);

    /**
     * 审核驳回订单
     *
     * @param orderId 订单ID
     * @param reviewerId 审核人ID
     * @param reviewerName 审核人姓名
     * @param reviewComment 审核意见
     * @param rejectReason 驳回原因
     * @param improvementSuggestions 改进建议
     * @return 审核记录ID
     */
    Long rejectOrder(Long orderId, Long reviewerId, String reviewerName, String reviewComment,
                     String rejectReason, String improvementSuggestions);

    /**
     * 退回订单修改
     *
     * @param orderId 订单ID
     * @param reviewerId 审核人ID
     * @param reviewerName 审核人姓名
     * @param reviewComment 审核意见
     * @param returnReason 退回原因
     * @param modificationRequirements 修改要求
     * @return 审核记录ID
     */
    Long returnOrderForModification(Long orderId, Long reviewerId, String reviewerName, String reviewComment,
                                    String returnReason, String modificationRequirements);

    /**
     * 转交审核
     *
     * @param orderId 订单ID
     * @param currentReviewerId 当前审核人ID
     * @param nextReviewerId 下一审核人ID
     * @param nextReviewerName 下一审核人姓名
     * @param transferReason 转交原因
     * @return 审核记录ID
     */
    Long transferOrderReview(Long orderId, Long currentReviewerId, Long nextReviewerId,
                             String nextReviewerName, String transferReason);

    /**
     * 客户确认订单
     *
     * @param orderId 订单ID
     * @param customerId 客户ID
     * @param customerName 客户姓名
     * @param confirmComment 确认意见
     * @param customerFeedback 客户反馈
     * @return 审核记录ID
     */
    Long confirmOrderByCustomer(Long orderId, Long customerId, String customerName,
                                String confirmComment, String customerFeedback);

    /**
     * 取消订单审核
     *
     * @param orderId 订单ID
     * @param cancellerId 取消人ID
     * @param cancellerName 取消人姓名
     * @param cancelReason 取消原因
     * @return 审核记录ID
     */
    Long cancelOrderReview(Long orderId, Long cancellerId, String cancellerName, String cancelReason);

    /**
     * 获取订单审核记录列表
     *
     * @param orderId 订单ID
     * @return 审核记录列表
     */
    List<OrderReviewRecord> getOrderReviewRecords(Long orderId);

    /**
     * 按评审时间倒序获取订单评审记录
     *
     * @param orderId 订单ID
     * @return 评审记录列表
     */
    List<OrderReviewRecord> listByOrder(Long orderId);

    /**
     * 获取订单审核历史
     *
     * @param orderId 订单ID
     * @return 审核历史记录
     */
    List<OrderReviewRecord> getOrderReviewHistory(Long orderId);

    /**
     * 获取当前审核信息
     *
     * @param orderId 订单ID
     * @return 当前审核记录
     */
    OrderReviewRecord getCurrentReviewInfo(Long orderId);

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

    /**
     * 批量提交审核
     *
     * @param orderIds 订单ID列表
     * @param submitterId 提交人ID
     * @param submitterName 提交人姓名
     * @return 提交结果
     */
    Object batchSubmitForReview(List<Long> orderIds, Long submitterId, String submitterName);

    /**
     * 批量审核通过
     *
     * @param orderIds 订单ID列表
     * @param reviewerId 审核人ID
     * @param reviewerName 审核人姓名
     * @return 审核结果
     */
    Object batchApproveOrders(List<Long> orderIds, Long reviewerId, String reviewerName);

    /**
     * 批量审核驳回
     *
     * @param orderIds 订单ID列表
     * @param reviewerId 审核人ID
     * @param reviewerName 审核人姓名
     * @param rejectReason 驳回原因
     * @return 审核结果
     */
    Object batchRejectOrders(List<Long> orderIds, Long reviewerId, String reviewerName, String rejectReason);
}
