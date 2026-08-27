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

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class QuotationEditOperLogTest {

    @Test
    void shouldBuildHeaderAndItemChangesWithoutDecimalScaleNoise() {
        OperLogChangeRecorder recorder = new OperLogChangeRecorder(mock(LogSaveService.class));
        QuotationServiceImpl service = new QuotationServiceImpl(
            mock(QuotationMapper.class), mock(QuotationFlowMapper.class),
            mock(SalesQuotationItemMapper.class), mock(SalesInquiryMapper.class),
            mock(ProductMapper.class), mock(IProductService.class), mock(IOrderService.class),
            mock(OrderMapper.class), mock(ISysAttachmentService.class),
            mock(RedisSequenceService.class), mock(PdfConfigLoader.class), recorder);
        SalesQuotation oldQuotation = quotation("USD", "7.20", item("P001", 10, "12.00"));
        SalesQuotation newQuotation = quotation("CNY", "7.200", item("P001", 20, "11.50"));

        List<String> changes = service.buildQuotationChanges(oldQuotation, newQuotation);

        assertTrue(changes.contains("币种:USD→CNY"));
        assertTrue(changes.contains("明细[P001]数量:10→20"));
        assertTrue(changes.contains("明细[P001]单价:12.00→11.50"));
        assertEquals(3, changes.size());
        assertTrue(recorder.toDetailJson(changes).contains("\"changes\""));
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
