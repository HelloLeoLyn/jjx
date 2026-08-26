package com.jjx.sales.service.impl;

import com.jjx.framework.common.RedisSequenceService;
import com.jjx.product.mapper.ProductMapper;
import com.jjx.product.service.IProductService;
import com.jjx.product.service.ProductCodeService;
import com.jjx.sales.domain.entity.SalesInquiry;
import com.jjx.sales.domain.entity.SalesQuotation;
import com.jjx.sales.mapper.QuotationMapper;
import com.jjx.sales.mapper.SalesInquiryMapper;
import com.jjx.sales.mapper.SalesQuotationItemMapper;
import com.jjx.sales.service.IQuotationService;
import com.jjx.system.annotation.BusinessType;
import com.jjx.system.domain.entity.SysOperLog;
import com.jjx.system.service.LogSaveService;
import com.jjx.system.service.OperLogChangeRecorder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class InquiryConversionOperLogTest {

    @Test
    void shouldBuildInquiryUpdateBeforeQuotationInsert() {
        InquiryServiceImpl service = new InquiryServiceImpl(
            mock(SalesInquiryMapper.class), mock(QuotationMapper.class),
            mock(SalesQuotationItemMapper.class), mock(RedisSequenceService.class),
            mock(ProductMapper.class), mock(IProductService.class),
            mock(ProductCodeService.class), mock(IQuotationService.class),
            mock(OperLogChangeRecorder.class), mock(LogSaveService.class));
        SalesInquiry inquiry = new SalesInquiry();
        inquiry.setInquiryId(3L);
        inquiry.setInquiryNo("INQ2608260001");
        inquiry.setTraceId("ae3f518682ab4a21");
        SalesQuotation quotation = new SalesQuotation();
        quotation.setQuotationId(12L);
        quotation.setQuotationNo("QT2608260002");

        List<SysOperLog> logs = service.buildConversionOperLogs(inquiry, quotation);

        assertEquals(2, logs.size());
        assertEquals("inquiry", logs.get(0).getBizType());
        assertEquals(BusinessType.UPDATE.getCode(), logs.get(0).getBusinessType());
        assertEquals("quotation", logs.get(1).getBizType());
        assertEquals(BusinessType.INSERT.getCode(), logs.get(1).getBusinessType());
        assertEquals(logs.get(0).getTraceId(), logs.get(1).getTraceId());
        assertEquals(logs.get(0).getCreateTime(), logs.get(1).getCreateTime());
    }
}
