package com.jjx.sales.service;

import com.jjx.sales.domain.dto.ODRSendToCustomerDTO;
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
     * 发送客户确认
     */
    void sendToCustomer(ODRSendToCustomerDTO dto);

    /**
     * 开始生产 - 创建生产工单并更新销售订单状态
     */
    void startProduction(Long orderId);
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
