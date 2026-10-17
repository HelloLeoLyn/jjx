package com.jjx.sales.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.jjx.event.EventPublisher;
import com.jjx.framework.common.RedisSequenceService;
import com.jjx.product.mapper.ProductMapper;
import com.jjx.product.service.IProductService;
import com.jjx.product.service.ProductCustomerValidator;
import com.jjx.sales.domain.converter.SalesQuotationConverter;
import com.jjx.sales.domain.entity.SalesQuotation;
import com.jjx.sales.domain.entity.SalesQuotationItem;
import com.jjx.sales.enums.QuotationStatus;
import com.jjx.sales.mapper.OrderMapper;
import com.jjx.sales.mapper.QuotationFlowMapper;
import com.jjx.sales.mapper.QuotationMapper;
import com.jjx.sales.mapper.SalesInquiryMapper;
import com.jjx.sales.mapper.SalesQuotationItemMapper;
import com.jjx.sales.service.IOrderService;
import com.jjx.system.mapper.SysAttachmentMapper;
import com.jjx.system.mapper.SysAttachmentRefMapper;
import com.jjx.system.service.AttachmentRefService;
import com.jjx.system.service.ISysAttachmentService;
import com.jjx.system.service.OperLogChangeRecorder;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class QuotationCopyPreviewTest {

    @Test
    void copyReturnsNewDraftWithFreshNumberAndUnsavedDetails() {
        QuotationMapper quotations = mock(QuotationMapper.class);
        SalesQuotationItemMapper items = mock(SalesQuotationItemMapper.class);
        RedisSequenceService sequences = mock(RedisSequenceService.class);
        SalesQuotation original = new SalesQuotation();
        original.setQuotationId(41L);
        original.setQuotationNo("QT261017004");
        original.setQuotationStatus(QuotationStatus.APPROVED.getValue());
        original.setDeleted(0);
        original.setCustomerName("Acme");
        when(quotations.selectById(41L)).thenReturn(original);
        SalesQuotationItem sourceItem = new SalesQuotationItem();
        sourceItem.setItemId(90L);
        sourceItem.setQuotationId(41L);
        sourceItem.setProductCode("P-1");
        sourceItem.setQuantity(3);
        sourceItem.setUnitPrice(new BigDecimal("12.50"));
        when(items.selectList(any())).thenReturn(List.of(sourceItem));
        when(sequences.generateBusinessNumberByType("quotation", "QT", "yyMMdd", 3))
                .thenReturn("QT261017005", "QT261017006");
        when(quotations.selectCount(any(Wrapper.class))).thenReturn(0L);

        QuotationServiceImpl service = new QuotationServiceImpl(
                quotations,
                mock(QuotationFlowMapper.class),
                items,
                mock(SalesInquiryMapper.class),
                mock(ProductMapper.class),
                mock(ProductCustomerValidator.class),
                mock(IProductService.class),
                mock(IOrderService.class),
                mock(OrderMapper.class),
                mock(ISysAttachmentService.class),
                sequences,
                mock(OperLogChangeRecorder.class),
                mock(SysAttachmentMapper.class),
                mock(SysAttachmentRefMapper.class),
                new AttachmentRefService(null, null, null, null, null, null, null),
                mock(SalesQuotationConverter.class),
                mock(EventPublisher.class));

        SalesQuotation first = service.copyQuotation(41L);
        SalesQuotation second = service.copyQuotation(41L);

        assertNull(first.getQuotationId());
        assertNull(first.getTraceId());
        assertEquals("COPY_QT261017005", first.getQuotationNo());
        assertEquals(QuotationStatus.DRAFT.getValue(), first.getQuotationStatus());
        assertEquals("Acme", first.getCustomerName());
        assertEquals(1, first.getItems().size());
        SalesQuotationItem copiedItem = first.getItems().get(0);
        assertNull(copiedItem.getItemId());
        assertNull(copiedItem.getQuotationId());
        assertEquals("P-1", copiedItem.getProductCode());
        assertEquals(3, copiedItem.getQuantity());
        assertEquals(new BigDecimal("12.50"), copiedItem.getUnitPrice());
        assertEquals("COPY_QT261017006", second.getQuotationNo());
        assertEquals(90L, sourceItem.getItemId());
        verify(quotations, never()).insert(any(SalesQuotation.class));
    }
}
