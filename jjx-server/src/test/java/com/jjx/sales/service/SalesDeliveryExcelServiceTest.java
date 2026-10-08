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
import java.io.InputStream;
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
    void fillsOriginalTemplateWithoutChangingItsLayoutOrStyles() throws Exception {
        byte[] bytes = SalesDeliveryExcelService.buildWorkbook(delivery(1), "SO261007001", "测试公司", "苏州");
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes));
             InputStream input = getClass().getResourceAsStream(SalesDeliveryExcelService.TEMPLATE);
             XSSFWorkbook template = new XSSFWorkbook(input)) {
            var sheet = workbook.getSheetAt(0);
            var original = template.getSheetAt(0);
            assertFalse(sheet.getProtect());
            assertEquals(original.getMergedRegions(), sheet.getMergedRegions());
            for (int col = 0; col < 9; col++) assertEquals(original.getColumnWidth(col), sheet.getColumnWidth(col));
            for (int row = 2; row <= 14; row++) {
                assertEquals(original.getRow(row).getHeight(), sheet.getRow(row).getHeight());
                for (int col = 0; col < 9; col++) {
                    assertEquals(original.getRow(row).getCell(col).getCellStyle().getIndex(),
                            sheet.getRow(row).getCell(col).getCellStyle().getIndex());
                }
            }
            assertEquals("测试公司", sheet.getRow(2).getCell(0).getStringCellValue());
            assertEquals("地址:苏州", sheet.getRow(4).getCell(6).getStringCellValue());
            assertEquals(CellType.STRING, sheet.getRow(5).getCell(1).getCellType());
            assertTrue(sheet.getRow(5).getCell(1).getStringCellValue().contains("=HYPERLINK"));
            assertTrue(sheet.getRow(6).getCell(0).getStringCellValue().contains("013800000001"));
            assertEquals("JST003MEOO-0", sheet.getRow(8).getCell(1).getStringCellValue());
            assertEquals(CellType.NUMERIC, sheet.getRow(8).getCell(4).getCellType());
            assertEquals(2, sheet.getRow(8).getCell(4).getNumericCellValue());
            assertEquals(325, sheet.getRow(8).getCell(5).getNumericCellValue());
            assertEquals(650, sheet.getRow(8).getCell(6).getNumericCellValue());
            assertEquals("SO261007001", sheet.getRow(8).getCell(7).getStringCellValue());
            assertEquals(CellType.BLANK, sheet.getRow(9).getCell(1).getCellType());
            assertEquals(CellType.BLANK, sheet.getRow(14).getCell(2).getCellType());
            var data = workbook.getSheet("发货数据");
            assertEquals(650, data.getRow(11).getCell(1).getNumericCellValue());
            assertEquals(16, data.getRow(12).getCell(1).getNumericCellValue());
            assertEquals(5, data.getRow(13).getCell(1).getNumericCellValue());
            assertEquals("薄膜开关", data.getRow(19).getCell(2).getStringCellValue());
            assertEquals(PrintSetup.A4_PAPERSIZE, sheet.getPrintSetup().getPaperSize());
            assertEquals(1, sheet.getPrintSetup().getFitWidth());
            assertEquals(1, sheet.getPrintSetup().getFitHeight());
            assertEquals(15d / 25.4, sheet.getMargin(PageMargin.LEFT), 0.0001);
            assertEquals(15d / 25.4, sheet.getMargin(PageMargin.RIGHT), 0.0001);
        }
    }

    @Test
    void copiesFiveLineFormsWithoutLosingOrRepeatingItemsOrLongSpecifications() throws Exception {
        SalesDeliveryVO delivery = delivery(12);
        String longSpec = "长规格测试".repeat(20);
        delivery.getItems().get(0).setSpecification(longSpec);
        byte[] bytes = SalesDeliveryExcelService.buildWorkbook(delivery, "SO261007001", "测试公司", "苏州");
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertEquals(4, workbook.getNumberOfSheets());
            for (int i = 0; i < 12; i++) {
                var sheet = workbook.getSheetAt(i / 5);
                int row = 8 + i % 5;
                assertEquals("JST003MEOO-" + i, sheet.getRow(row).getCell(1).getStringCellValue());
                assertEquals(i + 1, sheet.getRow(row).getCell(0).getNumericCellValue());
                assertEquals(27, sheet.getRow(row).getHeightInPoints());
                assertEquals(workbook.getSheetAt(0).getMergedRegions(), sheet.getMergedRegions());
                assertEquals(PrintSetup.A4_PAPERSIZE, sheet.getPrintSetup().getPaperSize());
                assertFalse(sheet.getPrintSetup().getLandscape());
                assertEquals(1, sheet.getPrintSetup().getFitHeight());
            }
            assertEquals(CellType.BLANK, workbook.getSheetAt(2).getRow(10).getCell(1).getCellType());
            assertEquals(longSpec, workbook.getSheetAt(0).getRow(8).getCell(2).getStringCellValue());
            assertEquals(longSpec, workbook.getSheet("发货数据").getRow(19).getCell(3).getStringCellValue());
        }
    }

    @Test
    void refusesLegacyMissingSnapshotInsteadOfExportingEntireOrder() {
        var service = new SalesDeliveryExcelService(null, null, null);
        BusinessException error = assertThrows(BusinessException.class, () -> service.export(delivery(0)));
        assertTrue(error.getMessage().contains("本次发货明细"));
    }
}
