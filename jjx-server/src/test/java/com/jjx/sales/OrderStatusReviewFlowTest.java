package com.jjx.sales;

import com.jjx.production.service.ProductionOrderService;
import com.jjx.sales.domain.entity.SalesOrder;
import com.jjx.sales.domain.vo.ReviewHistoryVO;
import com.jjx.sales.domain.vo.ReviewStatusVO;
import com.jjx.sales.enums.OrderStatusEnum;
import com.jjx.sales.mapper.OrderMapper;
import com.jjx.sales.mapper.SalesOrderProductMapper;
import com.jjx.sales.service.ISalesOrderProductService;
import com.jjx.sales.service.impl.OrderStatusServiceImpl;
import com.jjx.event.EventPublisher;
import com.jjx.framework.common.RedisSequenceService;
import com.jjx.inventory.service.InventoryAlertService;
import com.jjx.inventory.service.OrderMaterialReserveService;
import com.jjx.inventory.service.OrderStockReserveService;
import com.jjx.product.domain.entity.Product;
import com.jjx.product.mapper.EngineeringBomMapper;
import com.jjx.product.mapper.EngineeringRoutingMapper;
import com.jjx.product.mapper.ProductMapper;
import com.jjx.system.domain.entity.ReviewFlow;
import com.jjx.system.service.ReviewFlowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrderStatusReviewFlowTest {

    private OrderMapper salesOrderMapper;
    private ReviewFlowService reviewFlowService;
    private OrderStatusServiceImpl service;

    @BeforeEach
    void setUp() {
        salesOrderMapper = mock(OrderMapper.class);
        reviewFlowService = mock(ReviewFlowService.class);
        service = new OrderStatusServiceImpl(
                salesOrderMapper,
                mock(ISalesOrderProductService.class),
                mock(ProductionOrderService.class),
                mock(SalesOrderProductMapper.class),
                mock(RedisSequenceService.class),
                mock(EngineeringBomMapper.class),
                mock(ProductMapper.class),
                mock(EngineeringRoutingMapper.class),
                mock(EventPublisher.class),
                mock(InventoryAlertService.class),
                mock(OrderStockReserveService.class),
                mock(OrderMaterialReserveService.class),
                reviewFlowService);
    }

    private SalesOrder order(int status) {
        SalesOrder order = new SalesOrder();
        order.setOrderId(10L);
        order.setOrderNo("SO-TEST-1");
        order.setOrderStatus(status);
        return order;
    }

    private ReviewFlow flow(long id, int round, String action, String operator, String comment) {
        ReviewFlow f = new ReviewFlow();
        f.setFlowId(id);
        f.setRoundNo(round);
        f.setActionCode(action);
        f.setActionName(action.equals("SUBMIT") ? "提交审核" : "审核通过");
        f.setOperatorId(100L);
        f.setOperatorName(operator);
        f.setFromStatus("1");
        f.setToStatus(action.equals("SUBMIT") ? "2" : "4");
        f.setComment(comment);
        f.setCreateTime(LocalDateTime.of(2026, 8, 28, 10, 0));
        return f;
    }

    @Test
    void reviewStatusReturnsLatestFlowInfo() {
        when(salesOrderMapper.selectById(10L)).thenReturn(order(OrderStatusEnum.APPROVED.getCode()));
        when(reviewFlowService.listByBiz("sales_order", 10L)).thenReturn(List.of(
                flow(1L, 1, "SUBMIT", "销售员", null),
                flow(2L, 1, "APPROVE", "审核员", "同意")));

        ReviewStatusVO vo = service.getReviewStatus(10L);

        assertEquals(10L, vo.getOrderId());
        assertEquals("SO-TEST-1", vo.getOrderNo());
        assertEquals(OrderStatusEnum.APPROVED.getCode(), vo.getOrderStatus());
        assertEquals("已审核", vo.getOrderStatusName());
        assertEquals("审核员", vo.getReviewerName());
        assertEquals("同意", vo.getReviewRemark());
    }

    @Test
    void reviewStatusWithoutFlowsReturnsOrderStateOnly() {
        when(salesOrderMapper.selectById(10L)).thenReturn(order(OrderStatusEnum.DRAFT.getCode()));
        when(reviewFlowService.listByBiz("sales_order", 10L)).thenReturn(List.of());

        ReviewStatusVO vo = service.getReviewStatus(10L);

        assertEquals("草稿", vo.getOrderStatusName());
        assertNull(vo.getReviewerName());
        assertNull(vo.getReviewRemark());
    }

    @Test
    void reviewHistoryMapsFlowsInOrder() {
        when(reviewFlowService.listByBiz("sales_order", 10L)).thenReturn(List.of(
                flow(1L, 1, "SUBMIT", "销售员", null),
                flow(2L, 1, "APPROVE", "审核员", "同意")));

        List<ReviewHistoryVO> history = service.getReviewHistory(10L);

        assertEquals(2, history.size());
        ReviewHistoryVO submit = history.get(0);
        assertEquals("SUBMIT", submit.getActionType());
        assertEquals("提交审核", submit.getActionName());
        assertEquals("销售员", submit.getOperatorName());
        assertEquals("2", submit.getResult());
        ReviewHistoryVO approve = history.get(1);
        assertEquals("APPROVE", approve.getActionType());
        assertEquals("审核员", approve.getOperatorName());
        assertEquals("同意", approve.getRemark());
    }
}
