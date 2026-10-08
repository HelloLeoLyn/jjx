package com.jjx.sales.service;

import com.jjx.common.exception.BusinessException;
import com.jjx.sales.domain.entity.SalesDeliveryItem;
import com.jjx.sales.domain.vo.SalesDeliveryVO;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.PageMargin;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SalesDeliveryExcelServiceTest {
    private SalesDeliveryVO delivery(int count) {
        SalesDeliveryVO delivery = new SalesDeliveryVO();
        delivery.setDeliveryNo("DL261008001");
        delivery.setCustomerName("=HYPERLINK(\"https://example.com\")");
        delivery.setContactPhone("013800000001");
        delivery.setTotalAmount(new BigDecimal("650.00"));
        delivery.setFreightAmount(new BigDecimal("16.00"));
        delivery.setInsuranceAmount(new BigDecimal("5.00"));
        List<SalesDeliveryItem> items = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            SalesDeliveryItem item = new SalesDeliveryItem();
            item.setProductCode("JST003MEOO-" + i);
            item.setProductName("薄膜开关");
            item.setSpecification("客户规格");
            item.setQuantity(2);
            item.setUnitPrice(new BigDecimal("325.00"));
            item.setAmount(new BigDecimal("650.00"));
            items.add(item);
        }
        delivery.setItems(items);
        return delivery;
    }

    @Test
    void exportsEditableCellsWithSnapshotNumbersAndSeparateFees() throws Exception {
        byte[] bytes = SalesDeliveryExcelService.buildWorkbook(delivery(1), "SO261007001", "测试公司", "苏州");
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = workbook.getSheetAt(0);
            assertFalse(sheet.getProtect());
            assertEquals(CellType.STRING, sheet.getRow(3).getCell(0).getCellType());
            assertTrue(sheet.getRow(3).getCell(0).getStringCellValue().contains("=HYPERLINK"));
            assertTrue(sheet.getRow(4).getCell(0).getStringCellValue().contains("013800000001"));
            assertTrue(sheet.getRow(8).getCell(1).getStringCellValue().contains("JST003MEOO-0"));
            assertEquals(CellType.NUMERIC, sheet.getRow(8).getCell(4).getCellType());
            assertEquals(2, sheet.getRow(8).getCell(4).getNumericCellValue());
            assertEquals(325, sheet.getRow(8).getCell(5).getNumericCellValue());
            assertEquals(650, sheet.getRow(8).getCell(6).getNumericCellValue());
            assertEquals("SO261007001", sheet.getRow(8).getCell(7).getStringCellValue());
            assertEquals(650, sheet.getRow(13).getCell(6).getNumericCellValue());
            assertEquals(16, sheet.getRow(14).getCell(6).getNumericCellValue());
            assertEquals(5, sheet.getRow(15).getCell(6).getNumericCellValue());
            assertEquals("#,##0.00", sheet.getRow(8).getCell(6).getCellStyle().getDataFormatString());
            assertEquals(PrintSetup.A4_PAPERSIZE, sheet.getPrintSetup().getPaperSize());
            assertEquals(1, sheet.getPrintSetup().getFitWidth());
            assertEquals(0, sheet.getPrintSetup().getFitHeight());
            assertEquals(15d / 25.4, sheet.getMargin(PageMargin.LEFT), 0.0001);
            assertEquals(15d / 25.4, sheet.getMargin(PageMargin.RIGHT), 0.0001);
            assertEquals(7, sheet.getRepeatingRows().getLastRow());
        }
    }

    @Test
    void retainsAllRowsWhenDeliveryHasMoreThanFiveItems() throws Exception {
        SalesDeliveryVO delivery = delivery(12);
        String longSpec = "长规格测试".repeat(20);
        delivery.getItems().get(0).setSpecification(longSpec);
        byte[] bytes = SalesDeliveryExcelService.buildWorkbook(delivery, "SO261007001", "测试公司", "苏州");
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = workbook.getSheetAt(0);
            assertTrue(sheet.getRow(19).getCell(1).getStringCellValue().contains("JST003MEOO-11"));
            assertEquals(12, sheet.getRow(19).getCell(0).getNumericCellValue());
            assertEquals(longSpec, sheet.getRow(8).getCell(2).getStringCellValue());
            assertTrue(sheet.getRow(8).getHeightInPoints() > 48);
            assertTrue(sheet.getRow(20).getCell(0).getStringCellValue().contains("单据合计金额"));
        }
    }

    @Test
    void refusesLegacyMissingSnapshotInsteadOfExportingEntireOrder() {
        var service = new SalesDeliveryExcelService(null, null, null);
        BusinessException error = assertThrows(BusinessException.class, () -> service.export(delivery(0)));
        assertTrue(error.getMessage().contains("本次发货明细"));
    }
}
