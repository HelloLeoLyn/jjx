package com.jjx.sales;

import com.jjx.common.exception.BusinessException;
import com.jjx.sales.domain.dto.*;
import com.jjx.sales.domain.entity.*;
import com.jjx.sales.enums.QuotationStatus;
import com.jjx.sales.mapper.*;
import com.jjx.sales.service.IOrderService;
import com.jjx.sales.service.impl.QuotationServiceImpl;
import com.jjx.system.mapper.SysAttachmentRefMapper;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class QuotationOrderDraftTest {
    static <T> T service(Class<T> type, Map<Class<?>, Object> dependencies) {
        try {
            var ctor = Arrays.stream(type.getDeclaredConstructors()).max(Comparator.comparingInt(java.lang.reflect.Constructor::getParameterCount)).orElseThrow();
            return type.cast(ctor.newInstance(Arrays.stream(ctor.getParameterTypes()).map(t -> dependencies.containsKey(t) ? dependencies.get(t) : mock(t)).toArray()));
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
    QuotationMapper quotations = mock(QuotationMapper.class);
    SalesQuotationItemMapper items = mock(SalesQuotationItemMapper.class);
    QuotationFlowMapper flows = mock(QuotationFlowMapper.class);
    IOrderService orders = mock(IOrderService.class);
    SysAttachmentRefMapper attachments = mock(SysAttachmentRefMapper.class);
    com.jjx.system.service.AttachmentDocumentTypeService documentTypes = mock(com.jjx.system.service.AttachmentDocumentTypeService.class);
    SalesQuotation quote;
    QuotationServiceImpl service;
    @BeforeEach void setup() {
        service = service(QuotationServiceImpl.class, Map.of(QuotationMapper.class, quotations, SalesQuotationItemMapper.class, items,
                IOrderService.class, orders, QuotationFlowMapper.class, flows, SysAttachmentRefMapper.class, attachments, com.jjx.system.service.AttachmentDocumentTypeService.class, documentTypes));
        quote = new SalesQuotation(); quote.setQuotationId(1L); quote.setCustomerId(7L); quote.setCustomerName("customer"); quote.setQuotationNo("Q1");
        quote.setQuotationStatus(QuotationStatus.ACCEPTED.getValue()); quote.setDeleted(0); quote.setContactPerson("contact"); quote.setTraceId("trace");
        when(quotations.selectOne(any())).thenReturn(quote);
        var item = new SalesQuotationItem(); item.setItemId(1L); item.setProductCode("P1"); item.setProductName("product"); item.setQuantity(2);
        when(items.selectList(any())).thenReturn(List.of(item)); when(flows.insert(any(SalesQuotationFlow.class))).thenReturn(1);
        when(quotations.updateById(any(SalesQuotation.class))).thenReturn(1); when(orders.insertOrder(any())).thenReturn(9L);
    }
    @Test void previewHasNoWritesAndCopiesSource() {
        var draft = service.getOrderDraft(1L); assertEquals("contact", draft.getOrder().getContactPerson());
        assertEquals("trace", draft.getOrder().getTraceId()); assertNull(draft.getOrder().getOrderNo());
        assertEquals(2, draft.getOrder().getItems().getFirst().getQuantity()); verifyNoInteractions(orders, flows);
        verify(quotations, never()).updateById(any(SalesQuotation.class));
    }
    @Test void staleSourceAndChangedCustomerRejectWithoutCreatingOrder() {
        var stale = service.getOrderDraft(1L); quote.setContactPhone("changed");
        assertThrows(BusinessException.class, () -> service.convertToOrder(1L, stale)); verifyNoInteractions(orders);
        var wrong = service.getOrderDraft(1L); wrong.getOrder().setCustomerId(999L);
        assertThrows(BusinessException.class, () -> service.convertToOrder(1L, wrong)); verifyNoInteractions(orders);
    }
    @Test void successUsesInternalFlagAndSourceThenRejectsRepeat() {
        var draft = service.getOrderDraft(1L); assertEquals(9L, service.convertToOrder(1L, draft));
        verify(orders).insertOrder(argThat(d -> d.isQuotationConversionValidated() && d.getQuotationId().equals(1L) && d.getTraceId().equals("trace")));
        assertThrows(BusinessException.class, () -> service.convertToOrder(1L, draft));
    }
    @Test void conversionBindsOnlyOrderUploadsAndKeepsQuotationFilesOnOriginalSource() {
        var drawing = new com.jjx.system.domain.entity.SysAttachmentRef();
        drawing.setId(3L); drawing.setAttachmentId(213L); drawing.setDocumentType("CUSTOMER_DRAWING");
        var evidence = new com.jjx.system.domain.entity.SysAttachmentRef();
        evidence.setId(4L); evidence.setAttachmentId(214L); evidence.setDocumentType("SALES_QUOTATION_CUSTOMER_EVIDENCE");
        when(attachments.selectList(any())).thenReturn(List.of(drawing, evidence));
        when(documentTypes.supports(any(), eq("sales_order"))).thenReturn(true);
        var draft = service.getOrderDraft(1L);
        var upload = new SalesOrderAddDTO.OrderAttachmentDTO();
        upload.setAttachmentId(221L); upload.setDocumentType("PRODUCT_SPECIFICATION");
        var input = new ArrayList<>(draft.getOrder().getAttachments());
        input.add(upload); draft.getOrder().setAttachments(input);
        assertEquals(9L, service.convertToOrder(1L, draft));
        verify(orders).insertOrder(argThat(order -> order.getTraceId().equals("trace")
            && order.getAttachments().size() == 1 && order.getAttachments().getFirst().getAttachmentId().equals(221L)));
        verify(attachments, never()).insert(any(com.jjx.system.domain.entity.SysAttachmentRef.class));
        verify(attachments, never()).updateById(any(com.jjx.system.domain.entity.SysAttachmentRef.class));
        verify(attachments, never()).deleteById(anyLong());
    }

    @Test void internalFlagCannotBeForgedThroughJson() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        assertFalse(mapper.readValue("{\"quotationConversionValidated\":true}", SalesOrderAddDTO.class).isQuotationConversionValidated());
    }
}
