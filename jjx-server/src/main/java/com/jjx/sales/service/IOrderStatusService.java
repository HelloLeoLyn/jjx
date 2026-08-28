package com.jjx.sales.service;

import com.jjx.sales.domain.dto.ReviewDTO;
import com.jjx.sales.domain.vo.ReviewHistoryVO;
import com.jjx.sales.domain.vo.ReviewStatusVO;

import java.util.List;

public interface IOrderStatusService {

    /**
     * 提交审核
     */
    void submitReview(Long orderId);

    /**
     * 开始审核
     */
    void startReview(Long orderId);

    /**
     * 审核通过
     */
    void approveOrder(ReviewDTO reviewDTO);

    /**
     * 审核驳回
     */
    void rejectOrder(ReviewDTO reviewDTO);


    /**
     * 重新提交（驳回后重新编辑提交）
     */
    void resubmit(Long orderId);

    /**
     * 取消订单
     */
    void cancelOrder(Long orderId, String reason);

    /**
     * 生成生产计划（标准模式：SO→PLAN→审批→转工单）
     */
    void createProductionPlan(Long orderId);
    
    void confirmOrder(Long orderId, String confirmedBy, String confirmMethod, String remark);

    /**
     * 完成订单 - 已发货订单完结（SHIPPED -> COMPLETED）
     */
    void completeOrder(Long orderId);

    /**
     * 发货（025：IN_PRODUCTION→SHIPPED）
     */
    void shipOrder(Long orderId);

    /**
     * 获取订单审核状态
     */
    ReviewStatusVO getReviewStatus(Long orderId);

    /**
     * 获取订单审核历史
     */
    List<ReviewHistoryVO> getReviewHistory(Long orderId);
}
