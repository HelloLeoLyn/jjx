package com.jjx.production.controller;

import com.jjx.production.controller.QualityTemplateRegistryController;
import com.jjx.sales.controller.SalesDeliveryController;
import com.jjx.sales.controller.SalesInvoiceController;
import com.jjx.sales.controller.SalesReceiptController;
import com.jjx.system.annotation.Log;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 打印日志必须按业务对象归档，才能被业务流水查询到。 */
class PrintLogAnnotationContractTest {

    @Test
    void dedicatedPrintEndpointsUseBusinessTypes() throws Exception {
        assertEquals("'receipt'", annotation(SalesReceiptController.class, "printLog", Long.class).bizType());
        assertEquals("'invoice'", annotation(SalesInvoiceController.class, "printLog", Long.class).bizType());
        assertEquals("'sales_delivery'", annotation(SalesDeliveryController.class, "printLog", Long.class).bizType());
    }

    @Test
    void qualityTemplatePrintEndpointPassesThroughBusinessIdentity() throws Exception {
        Log log = annotation(QualityTemplateRegistryController.class, "printLog",
                Long.class, String.class, Long.class);
        assertTrue(log.bizType().contains("#bizType"));
        assertTrue(log.bizId().contains("#bizId"));
    }

    private static Log annotation(Class<?> type, String method, Class<?>... parameterTypes) throws Exception {
        return type.getMethod(method, parameterTypes).getAnnotation(Log.class);
    }
}
