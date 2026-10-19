package com.jjx.sales.service.impl;

import com.jjx.sales.domain.dto.SalesOrderAddDTO;
import com.jjx.sales.domain.entity.SalesQuotation;
import com.jjx.sales.mapper.SalesQuotationItemMapper;
import com.jjx.system.domain.entity.SysAttachmentRef;
import com.jjx.system.mapper.SysAttachmentRefMapper;
import com.jjx.system.service.AttachmentDocumentTypeService;
import com.jjx.product.mapper.ProductMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/** 报价转订单：按目标来源过滤证据类型（dev-20261019-001）。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QuotationOrderAttachmentFilterTest {

    private static final String QUOTATION_ONLY = "SALES_QUOTATION_REVIEW_EVIDENCE";
    private static final String NORMAL = "CUSTOMER_DRAWING";

    @Mock SalesQuotationItemMapper quotationItemMapper;
    @Mock ProductMapper productMapper;
    @Mock SysAttachmentRefMapper attachmentRefMapper;
    @Mock AttachmentDocumentTypeService attachmentDocumentTypeService;
    @InjectMocks QuotationServiceImpl service;

    private SysAttachmentRef ref(long id, String type) {
        SysAttachmentRef r = new SysAttachmentRef();
        r.setId(id);
        r.setAttachmentId(id);
        r.setDocumentType(type);
        return r;
    }

    private SalesOrderAddDTO build() throws Exception {
        SalesQuotation q = new SalesQuotation();
        q.setQuotationId(1L);
        q.setQuotationNo("QT-1");
        q.setCustomerId(7L);
        q.setCustomerName("客户A");
        when(quotationItemMapper.selectList(any())).thenReturn(List.of());
        when(attachmentRefMapper.selectList(any())).thenReturn(List.of(
                ref(1L, QUOTATION_ONLY), ref(2L, NORMAL), ref(3L, null)));
        when(attachmentDocumentTypeService.supports(eq(QUOTATION_ONLY), anyString())).thenReturn(false);
        when(attachmentDocumentTypeService.supports(eq(NORMAL), anyString())).thenReturn(true);
        when(attachmentDocumentTypeService.supports(eq(null), anyString())).thenReturn(true);

        Method m = QuotationServiceImpl.class.getDeclaredMethod("buildQuotationOrder", SalesQuotation.class);
        m.setAccessible(true);
        return (SalesOrderAddDTO) m.invoke(service, q);
    }

    @Test
    void quotationOnlyEvidenceIsFilteredOut() throws Exception {
        SalesOrderAddDTO dto = build();
        assertEquals(2, dto.getAttachments().size());
        assertTrue(dto.getAttachments().stream().noneMatch(a -> QUOTATION_ONLY.equals(a.getDocumentType())));
        assertTrue(dto.getAttachments().stream().anyMatch(a -> NORMAL.equals(a.getDocumentType())));
        assertTrue(dto.getAttachments().stream().anyMatch(a -> a.getDocumentType() == null));
    }
}
