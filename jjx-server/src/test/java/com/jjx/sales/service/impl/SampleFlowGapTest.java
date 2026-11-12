package com.jjx.sales.service.impl;

import com.jjx.common.exception.BusinessException;
import com.jjx.sales.domain.entity.SalesOrder;
import com.jjx.sales.domain.entity.SalesSampleOrder;
import com.jjx.sales.domain.entity.SalesSampleRound;
import com.jjx.sales.enums.SampleOrderStatusEnum;
import com.jjx.sales.mapper.OrderMapper;
import com.jjx.sales.mapper.SalesSampleBomMapper;
import com.jjx.sales.mapper.SalesSampleOrderMapper;
import com.jjx.sales.mapper.SalesSampleRoundMapper;
import com.jjx.system.utils.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 样品单流程缺口（dev-20261106-009）：接单补轮次快照、确认方式必须有值。 */
@ExtendWith(MockitoExtension.class)
class SampleFlowGapTest {

    @Mock OrderMapper orderMapper;
    @Mock SalesSampleRoundMapper sampleRoundMapper;
    @Mock SalesSampleOrderMapper sampleOrderMapper;
    @Mock SalesSampleBomMapper sampleBomMapper;
    @InjectMocks SampleOrderServiceImpl service;

    private SalesOrder sampleOrder(Integer status, Integer round) {
        SalesOrder order = new SalesOrder();
        order.setOrderId(10L);
        order.setDeleted(0);
        order.setOrderType(2);
        order.setSampleStatus(status);
        order.setSampleRound(round);
        return order;
    }

    @Test
    void acceptCreatesRoundSnapshotWhenMissing() {
        SalesOrder order = sampleOrder(SampleOrderStatusEnum.REQUEST.getValue(), 2);
        when(orderMapper.selectById(10L)).thenReturn(order);
        when(orderMapper.acceptEngineering(eq(10L), anyInt(), anyInt(), anyString())).thenReturn(1);
        when(sampleRoundMapper.selectByOrderId(10L)).thenReturn(List.of());
        when(sampleRoundMapper.insert(any(SalesSampleRound.class))).thenReturn(1);
        when(sampleBomMapper.selectCount(any())).thenReturn(0L);

        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getRealName).thenReturn("engineer");
            service.acceptEngineering(10L);
        }

        ArgumentCaptor<SalesSampleRound> captor = ArgumentCaptor.forClass(SalesSampleRound.class);
        verify(sampleRoundMapper).insert(captor.capture());
        assertEquals(2, captor.getValue().getRoundNo());
        assertEquals("pending", captor.getValue().getResult());
        assertEquals(10L, captor.getValue().getOrderId());
    }

    @Test
    void acceptDoesNotDuplicateExistingRound() {
        SalesOrder order = sampleOrder(SampleOrderStatusEnum.REQUEST.getValue(), 2);
        when(orderMapper.selectById(10L)).thenReturn(order);
        when(orderMapper.acceptEngineering(eq(10L), anyInt(), anyInt(), anyString())).thenReturn(1);
        SalesSampleRound existing = new SalesSampleRound();
        existing.setRoundNo(2);
        when(sampleRoundMapper.selectByOrderId(10L)).thenReturn(List.of(existing));
        when(sampleBomMapper.selectCount(any())).thenReturn(0L);

        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getRealName).thenReturn("engineer");
            service.acceptEngineering(10L);
        }

        verify(sampleRoundMapper, never()).insert(any(SalesSampleRound.class));
    }

    @Test
    void confirmWritesDefaultMethodWhenMissing() {
        SalesOrder order = sampleOrder(SampleOrderStatusEnum.SAMPLE_SENT.getValue(), 1);
        when(orderMapper.selectByIdForUpdate(10L)).thenReturn(order);
        when(orderMapper.selectById(10L)).thenReturn(order);
        when(orderMapper.updateSampleStatus(eq(10L), anyInt(), anyInt())).thenReturn(1);
        when(sampleRoundMapper.selectByOrderId(10L)).thenReturn(List.of());
        when(sampleOrderMapper.selectByOrderId(10L)).thenReturn(null);
        when(sampleOrderMapper.insert(any(SalesSampleOrder.class))).thenReturn(1);

        service.confirmSample(10L, "客户A");

        ArgumentCaptor<SalesSampleOrder> captor = ArgumentCaptor.forClass(SalesSampleOrder.class);
        verify(sampleOrderMapper).insert(captor.capture());
        assertEquals("客户确认", captor.getValue().getConfirmMethod());
        assertEquals("客户A", captor.getValue().getConfirmBy());
    }

    @Test
    void rejectedTransitionDoesNotWriteConfirmMethod() {
        SalesOrder order = sampleOrder(SampleOrderStatusEnum.SAMPLE_SENT.getValue(), 1);
        when(orderMapper.selectByIdForUpdate(10L)).thenReturn(order);
        when(orderMapper.selectById(10L)).thenReturn(order);
        when(orderMapper.updateSampleStatus(eq(10L), anyInt(), anyInt())).thenReturn(0);

        assertThrows(BusinessException.class, () -> service.confirmSample(10L, "客户A"));
        verify(sampleOrderMapper, never()).insert(any(SalesSampleOrder.class));
        verify(sampleOrderMapper, never()).updateById(any(SalesSampleOrder.class));
    }
}
