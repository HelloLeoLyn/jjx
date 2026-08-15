package com.jjx.sales;

import com.jjx.common.exception.BusinessException;
import com.jjx.sales.domain.dto.SalesOrderAddDTO;
import com.jjx.sales.domain.entity.SalesQuotation;
import com.jjx.sales.domain.entity.SalesQuotationFlow;
import com.jjx.sales.enums.QuotationStatus;
import com.jjx.sales.mapper.QuotationFlowMapper;
import com.jjx.sales.mapper.QuotationMapper;
import com.jjx.sales.mapper.SalesQuotationItemMapper;
import com.jjx.sales.service.IOrderService;
import com.jjx.sales.service.impl.QuotationServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuotationInvariantTest {
    @Mock QuotationMapper quotationMapper;
    @Mock QuotationFlowMapper quotationFlowMapper;
    @Mock SalesQuotationItemMapper quotationItemMapper;
    @Mock IOrderService orderService;
    @InjectMocks QuotationServiceImpl service;

    @Test void illegalQuotationStatusJumpIsRejectedWithoutWrite() {
        when(quotationMapper.selectById(1L)).thenReturn(quotation(QuotationStatus.DRAFT));
        assertThrows(BusinessException.class,
                () -> service.updateQuotationStatus(1L, QuotationStatus.COMPLETED.getCode(), null));
        verify(quotationMapper, never()).updateById(any(SalesQuotation.class));
        verify(quotationFlowMapper, never()).insert(any(SalesQuotationFlow.class));
    }

    @Test void sentQuotationCannotBeDeleted() {
        when(quotationMapper.selectById(1L)).thenReturn(quotation(QuotationStatus.SENT));
        assertThrows(BusinessException.class, () -> service.deleteQuotationById(1L));
        verify(quotationMapper, never()).deleteById(any(Long.class));
    }

    @Test void quotationConversionPreservesCustomerAmountsCurrencyAndTrace() {
        SalesQuotation quotation = quotation(QuotationStatus.ACCEPTED);
        quotation.setQuotationNo("QT-001");
        quotation.setCustomerId(31L);
        quotation.setCustomerName("JJX Customer");
        quotation.setTraceId("trace-001");
        quotation.setSubtotalAmount(new BigDecimal("125.50"));
        quotation.setTaxRate(new BigDecimal("13"));
        quotation.setTaxAmount(new BigDecimal("16.32"));
        quotation.setDiscountAmount(new BigDecimal("5.00"));
        quotation.setCurrency("USD");
        quotation.setExchangeRate(new BigDecimal("7.20"));
        when(quotationMapper.selectById(1L)).thenReturn(quotation);
        when(quotationItemMapper.selectList(any())).thenReturn(List.of());
        when(orderService.generateOrderNo()).thenReturn("SO-001");
        when(orderService.insertOrder(any())).thenReturn(99L);

        assertEquals(99L, service.convertToOrder(1L));

        ArgumentCaptor<SalesOrderAddDTO> captor = ArgumentCaptor.forClass(SalesOrderAddDTO.class);
        verify(orderService).insertOrder(captor.capture());
        SalesOrderAddDTO order = captor.getValue();
        assertEquals(31L, order.getCustomerId());
        assertEquals("JJX Customer", order.getCustomerName());
        assertEquals("trace-001", order.getTraceId());
        assertEquals(new BigDecimal("125.50"), order.getTotalAmount());
        assertEquals(new BigDecimal("0.1300"), order.getTaxRate());
        assertEquals(new BigDecimal("16.32"), order.getTaxAmount());
        assertEquals(new BigDecimal("5.00"), order.getDiscountAmount());
        assertEquals("USD", order.getCurrency());
        assertEquals(new BigDecimal("7.20"), order.getExchangeRate());
        assertEquals(QuotationStatus.COMPLETED.getCode(), quotation.getQuotationStatus());
        assertEquals(99L, quotation.getConvertedOrderId());
    }

    @Test void completedQuotationCannotGenerateAnotherOrder() {
        when(quotationMapper.selectById(1L)).thenReturn(quotation(QuotationStatus.COMPLETED));
        assertThrows(BusinessException.class, () -> service.convertToOrder(1L));
        verify(orderService, never()).insertOrder(any());
    }

    private static SalesQuotation quotation(QuotationStatus status) {
        SalesQuotation quotation = new SalesQuotation();
        quotation.setQuotationId(1L);
        quotation.setQuotationStatus(status.getCode());
        quotation.setDeleted(0);
        return quotation;
    }
}
