package com.jjx.purchase;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jjx.purchase.domain.dto.PurchasePaymentDTO;
import com.jjx.purchase.domain.entity.PurchaseOrder;
import com.jjx.purchase.domain.entity.PurchasePayment;
import com.jjx.purchase.mapper.PurchaseOrderMapper;
import com.jjx.purchase.mapper.PurchasePaymentMapper;
import com.jjx.purchase.service.impl.PurchasePaymentServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PurchasePaymentSourceTest {
    static {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "payment-source-test");
        TableInfoHelper.initTableInfo(assistant, PurchaseOrder.class);
        TableInfoHelper.initTableInfo(assistant, PurchasePayment.class);
    }
    private final PurchasePaymentMapper payments = mock(PurchasePaymentMapper.class);
    private final PurchaseOrderMapper orders = mock(PurchaseOrderMapper.class);
    private final PurchasePaymentServiceImpl service = new PurchasePaymentServiceImpl(payments, null, orders);

    @Test void listEnrichesSourceInOneBatchAndPreservesMissingSourceAndPagination() {
        PurchasePayment first = new PurchasePayment(); first.setOrderId(1L);
        PurchasePayment second = new PurchasePayment(); second.setOrderId(1L);
        PurchasePayment missing = new PurchasePayment(); missing.setOrderId(9L);
        Page<PurchasePayment> page = new Page<>(2, 3, 10);
        page.setRecords(List.of(first, second, missing));
        when(payments.selectPage(any(Page.class), any())).thenReturn(page);
        PurchaseOrder order = new PurchaseOrder(); order.setOrderId(1L);
        order.setOrderNo("PO001"); order.setSupplierName("供应商");
        when(orders.selectBatchIds(any())).thenReturn(List.of(order));
        var result = service.selectPaymentList(new PurchasePaymentDTO());
        assertEquals(10, result.getTotal()); assertEquals(2, result.getPageNum());
        assertEquals(3, result.getRecords().size());
        assertEquals("PO001", first.getOrderNo()); assertEquals("供应商", second.getSupplierName());
        assertNull(missing.getOrderNo());
        verify(orders).selectBatchIds(List.of(1L, 9L));
        verifyNoMoreInteractions(orders);
    }

    @Test void orderNumberFiltersMatchingIdsAndNoMatchReturnsEmptyInsteadOfAll() {
        PurchasePaymentDTO query = new PurchasePaymentDTO(); query.setOrderNo(" PO001 ");
        PurchaseOrder order = new PurchaseOrder(); order.setOrderId(1L);
        when(orders.selectList(any())).thenReturn(List.of(order));
        LambdaQueryWrapper<PurchasePayment> wrapper = ReflectionTestUtils.invokeMethod(service, "buildPaymentQuery", query);
        assertTrue(wrapper.getSqlSegment().contains("order_id IN"));
        assertTrue(wrapper.getParamNameValuePairs().containsValue(1L));
        var captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(orders).selectList(captor.capture());
        captor.getValue().getSqlSegment();
        assertTrue(captor.getValue().getParamNameValuePairs().containsValue("%PO001%"));
        when(orders.selectList(any())).thenReturn(List.of());
        wrapper = ReflectionTestUtils.invokeMethod(service, "buildPaymentQuery", query);
        assertTrue(wrapper.getSqlSegment().contains("1 = 0"));
    }
}
