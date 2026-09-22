package com.jjx.sales.service;

import com.jjx.sales.domain.vo.OrderReviewProcessVO;
import java.util.List;

/** 订单审核只读查询；审核记录的唯一真源为 review_flow。 */
public interface IOrderReviewService {
    List<OrderReviewProcessVO> getOrderReviewRecords(Long orderId);
    boolean canSubmitForReview(Long orderId);
    boolean canConfirmByCustomer(Long orderId, Long customerId);
}
