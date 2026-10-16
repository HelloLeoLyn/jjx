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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 依赖用 @InjectMocks 注入（dev-20261016-053）：服务再加构造依赖时本类无需跟着改，
 * 避免「改实现 → 手写 new 的测试编译不过」反复发生（031/039 已各踩一次）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PurchasePaymentSourceTest {
    static {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "payment-source-test");
        TableInfoHelper.initTableInfo(assistant, PurchaseOrder.class);
        TableInfoHelper.initTableInfo(assistant, PurchasePayment.class);
    }
    @Mock private PurchasePaymentMapper payments;
    @Mock private PurchaseOrderMapper orders;
    /** 本类未用到的依赖也备成 mock：将来补用例走到该路径时不会空指针。 */
    @Mock private com.jjx.event.EventPublisher eventPublisher;
    @Mock private com.jjx.system.service.AttachmentRefService attachmentRefService;
    @InjectMocks private PurchasePaymentServiceImpl service;

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
