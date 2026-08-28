package com.jjx.sales.service.impl;

import com.jjx.common.utils.pdf.PdfConfigLoader;
import com.jjx.framework.common.RedisSequenceService;
import com.jjx.product.mapper.ProductMapper;
import com.jjx.product.service.IProductService;
import com.jjx.sales.domain.entity.SalesQuotation;
import com.jjx.sales.domain.entity.SalesQuotationItem;
import com.jjx.sales.mapper.OrderMapper;
import com.jjx.sales.mapper.QuotationFlowMapper;
import com.jjx.sales.mapper.QuotationMapper;
import com.jjx.sales.mapper.SalesInquiryMapper;
import com.jjx.sales.mapper.SalesQuotationItemMapper;
import com.jjx.sales.service.IOrderService;
import com.jjx.system.service.ISysAttachmentService;
import com.jjx.system.service.LogSaveService;
import com.jjx.system.service.OperLogChangeRecorder;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class QuotationEditOperLogTest {

    @Test
    void shouldDefaultValidUntilToThirtyDaysAfterQuotationDate() {
        QuotationMapper quotationMapper = mock(QuotationMapper.class);
        QuotationServiceImpl service = service(quotationMapper);
        SalesQuotation quotation = new SalesQuotation();
        quotation.setQuotationNo("QT-DEFAULT-DATE");
        quotation.setQuotationDate(LocalDate.of(2026, 8, 28));
        quotation.setSalesPersonId(1L);
        quotation.setSalesPersonName("tester");
        quotation.setItems(List.of());

        service.insertQuotation(quotation);

        ArgumentCaptor<SalesQuotation> captor = ArgumentCaptor.forClass(SalesQuotation.class);
        verify(quotationMapper).insert(captor.capture());
        assertEquals(LocalDate.of(2026, 9, 27), captor.getValue().getValidUntil());
    }

    @Test
    void shouldBuildHeaderAndItemChangesWithoutDecimalScaleNoise() {
        OperLogChangeRecorder recorder = new OperLogChangeRecorder(mock(LogSaveService.class));
        QuotationServiceImpl service = service(mock(QuotationMapper.class), recorder);
        SalesQuotation oldQuotation = quotation("USD", "7.20", item("P001", 10, "12.00"));
        SalesQuotation newQuotation = quotation("CNY", "7.200", item("P001", 20, "11.50"));

        List<String> changes = service.buildQuotationChanges(oldQuotation, newQuotation);

        assertTrue(changes.contains("币种:USD→CNY"));
        assertTrue(changes.contains("明细[P001]数量:10→20"));
        assertTrue(changes.contains("明细[P001]单价:12.00→11.50"));
        assertEquals(3, changes.size());
        assertTrue(recorder.toDetailJson(changes).contains("\"changes\""));
    }

    private static QuotationServiceImpl service(QuotationMapper quotationMapper) {
        return service(quotationMapper, new OperLogChangeRecorder(mock(LogSaveService.class)));
    }

    private static QuotationServiceImpl service(QuotationMapper quotationMapper, OperLogChangeRecorder recorder) {
        return new QuotationServiceImpl(
            quotationMapper, mock(QuotationFlowMapper.class),
            mock(SalesQuotationItemMapper.class), mock(SalesInquiryMapper.class),
            mock(ProductMapper.class), mock(IProductService.class), mock(IOrderService.class),
            mock(OrderMapper.class), mock(ISysAttachmentService.class),
            mock(RedisSequenceService.class), mock(PdfConfigLoader.class), recorder);
    }

    private static SalesQuotation quotation(String currency, String exchangeRate,
                                             SalesQuotationItem... items) {
        SalesQuotation quotation = new SalesQuotation();
        quotation.setCurrency(currency);
        quotation.setExchangeRate(new BigDecimal(exchangeRate));
        quotation.setItems(List.of(items));
        return quotation;
    }

    private static SalesQuotationItem item(String productCode, int quantity, String unitPrice) {
        SalesQuotationItem item = new SalesQuotationItem();
        item.setProductCode(productCode);
        item.setProductName(productCode);
        item.setQuantity(quantity);
        item.setUnitPrice(new BigDecimal(unitPrice));
        return item;
    }
}
