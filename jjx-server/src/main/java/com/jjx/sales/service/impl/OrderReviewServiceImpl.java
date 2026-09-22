package com.jjx.sales.service.impl;

import com.jjx.common.exception.BusinessException;
import com.jjx.sales.domain.entity.SalesOrder;
import com.jjx.sales.domain.vo.OrderReviewProcessVO;
import com.jjx.sales.enums.SalesOrderStatusEnum;
import com.jjx.sales.mapper.OrderMapper;
import com.jjx.sales.service.IOrderReviewService;
import com.jjx.system.domain.entity.ReviewFlow;
import com.jjx.system.service.ReviewFlowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderReviewServiceImpl implements IOrderReviewService {
    private final OrderMapper orderMapper;
    private final ReviewFlowService reviewFlowService;

    @Override
    public List<OrderReviewProcessVO> getOrderReviewRecords(Long orderId) {
        List<OrderReviewProcessVO> records = new ArrayList<>();
        SalesOrder order = orderMapper.selectById(orderId);
        String orderNo = order == null ? null : order.getOrderNo();
        for (ReviewFlow flow : reviewFlowService.listByBiz("sales_order", orderId)) {
            OrderReviewProcessVO vo = new OrderReviewProcessVO();
            vo.setRecordId(flow.getFlowId());
            vo.setOrderId(orderId);
            vo.setOrderNo(orderNo);
            vo.setReviewStage(flow.getRoundNo());
            vo.setStageName(flow.getActionName());
            vo.setReviewerName(flow.getOperatorName());
            vo.setReviewComment(flow.getComment());
            vo.setReviewTime(flow.getCreateTime());
            vo.setReviewResult(resultCodeOf(flow.getActionCode()));
            vo.setResultDescription(resultTextOf(flow.getActionCode()));
            records.add(vo);
        }
        return records;
    }

    @Override
    public boolean canSubmitForReview(Long orderId) {
        try {
            return SalesOrderStatusEnum.getByValue(getOrderById(orderId).getOrderStatus()).isSubmittable();
        } catch (Exception e) {
            log.error("检查订单是否可提交审核失败", e);
            return false;
        }
    }

    @Override
    public boolean canConfirmByCustomer(Long orderId, Long customerId) {
        try {
            SalesOrder order = getOrderById(orderId);
            return SalesOrderStatusEnum.getByValue(order.getOrderStatus()).isConfirmable()
                    && order.getCustomerId().equals(customerId);
        } catch (Exception e) {
            log.error("检查订单是否可由客户确认失败", e);
            return false;
        }
    }

    private SalesOrder getOrderById(Long orderId) {
        SalesOrder order = orderMapper.selectById(orderId);
        if (order == null || order.getDeleted() == 1) throw new BusinessException("订单不存在或已被删除");
        return order;
    }

    private static Integer resultCodeOf(String code) {
        if (code == null) return null;
        return switch (code.toUpperCase()) { case "APPROVE" -> 1; case "REJECT" -> 2; case "SUBMIT" -> 0; default -> null; };
    }

    private static String resultTextOf(String code) {
        if (code == null) return null;
        return switch (code.toUpperCase()) { case "APPROVE" -> "通过"; case "REJECT" -> "驳回"; case "SUBMIT" -> "已提交"; default -> null; };
    }
}
