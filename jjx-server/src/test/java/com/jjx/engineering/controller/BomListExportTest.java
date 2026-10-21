package com.jjx.engineering.controller;

import com.jjx.common.core.page.PageResult;
import com.jjx.product.service.IEngineeringBomService;
import com.jjx.product.service.IEngineeringBomService.BomListQuery;
import com.jjx.product.service.IEngineeringBomService.BomListRow;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayInputStream;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BomListExportTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void exportsEveryFilteredPageAndPreservesVersionDimension(boolean allVersions) throws Exception {
        var service = mock(IEngineeringBomService.class);
        when(service.getBomListPage(any(BomListQuery.class))).thenAnswer(invocation -> {
            BomListQuery query = invocation.getArgument(0);
            assertEquals(allVersions, query.isShowAllVersions());
            assertEquals("产品甲", query.getProductName());
            assertEquals(100, query.getPageSize());
            int from = (query.getPageNum() - 1) * 100;
            var rows = IntStream.range(from, Math.min(from + 100, 101)).mapToObj(i -> {
                var row = new BomListRow();
                row.setBomCode("BOM-" + i);
                row.setBomVersion("V" + i);
                return row;
            }).toList();
            return PageResult.build(rows, 101);
        });
        var controller = new BomController(service);
        var query = new BomListQuery();
        query.setPageNum(9);
        query.setPageSize(10);
        query.setProductName("产品甲");
        query.setShowAllVersions(allVersions);
        var response = new MockHttpServletResponse();
        controller.exportBom(query, response);
        verify(service, times(2)).getBomListPage(any(BomListQuery.class));
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            var sheet = workbook.getSheetAt(0);
            assertEquals(102, sheet.getPhysicalNumberOfRows());
            assertEquals("BOM-100", sheet.getRow(101).getCell(0).getStringCellValue());
            assertEquals("V100", sheet.getRow(101).getCell(1).getStringCellValue());
        }
    }
}
