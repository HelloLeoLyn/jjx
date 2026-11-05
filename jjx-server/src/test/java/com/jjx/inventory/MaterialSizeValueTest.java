package com.jjx.inventory;

import com.jjx.inventory.master.MaterialSizeValue;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MaterialSizeValueTest {
    @Test void excelDimensionColumnsConvertExplicitUnitsBeforeSave() throws Exception {
        var catalog = org.mockito.Mockito.mock(com.jjx.inventory.master.MaterialMasterCatalog.class);
        var service = org.mockito.Mockito.mock(com.jjx.inventory.master.MaterialMasterService.class);
        var materials = org.mockito.Mockito.mock(com.jjx.inventory.mapper.InventoryMaterialMapper.class);
        org.mockito.Mockito.when(catalog.dictionaries()).thenReturn(java.util.Map.of());
        org.mockito.Mockito.when(materials.selectList(org.mockito.ArgumentMatchers.any())).thenReturn(java.util.List.of());
        org.mockito.Mockito.when(service.prepare(org.mockito.ArgumentMatchers.any())).thenAnswer(call -> {
            com.jjx.inventory.master.MaterialMasterDTO dto = call.getArgument(0);
            MaterialSizeValue.validate(dto.getWidthMm(), dto.getLengthMm(), dto.getThicknessMm(), dto.getSizeUnit());
            var material = new com.jjx.inventory.domain.InventoryMaterial();
            material.setMaterialName(dto.getMaterialName()); material.setWidthMm(dto.getWidthMm());
            material.setLengthMm(dto.getLengthMm()); material.setThicknessMm(dto.getThicknessMm());
            return new com.jjx.inventory.master.MaterialMasterService.Prepared(material, java.util.List.of());
        });
        var excel = new com.jjx.inventory.master.MaterialMasterExcelService(catalog, service, materials,
                new com.fasterxml.jackson.databind.ObjectMapper(), null);
        for (String thickness : new String[]{"254μm", "254µm", "254um", "0.254", "0.254mm", "0.0254cm", "0.000254m", "错误μm"}) {
            try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(); var out = new java.io.ByteArrayOutputStream()) {
                var sheet = workbook.createSheet(); var header = sheet.createRow(0); var row = sheet.createRow(1);
                String[] headers = {"物料名称", "宽(mm)", "长(mm)", "厚(mm)", "尺寸单位"};
                String[] values = {"尺寸导入", "315mm", "1000m", thickness, "mm"};
                for (int i = 0; i < headers.length; i++) {
                    header.createCell(i).setCellValue(headers[i]); row.createCell(i).setCellValue(values[i]);
                }
                workbook.write(out);
                var file = new org.springframework.mock.web.MockMultipartFile("file", "sizes.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
                org.mockito.Mockito.clearInvocations(service);
                var result = excel.importExcel(file, null, false, "codex", false);
                if (thickness.equals("错误μm")) {
                    assertEquals(0, result.successCount());
                    assertTrue(result.errors().get(0).contains("厚(mm)"));
                    org.mockito.Mockito.verifyNoInteractions(service);
                } else {
                    assertEquals(java.util.List.of(), result.errors()); assertEquals(1, result.successCount());
                    var saved = org.mockito.ArgumentCaptor.forClass(com.jjx.inventory.master.MaterialMasterDTO.class);
                    org.mockito.Mockito.verify(service).save(saved.capture(), org.mockito.ArgumentMatchers.eq("codex"),
                            org.mockito.ArgumentMatchers.eq(com.jjx.inventory.classify.MaterialClassSource.IMPORT));
                    assertEquals(0, new java.math.BigDecimal("0.254").compareTo(saved.getValue().getThicknessMm()));
                    assertEquals(0, new java.math.BigDecimal("315").compareTo(saved.getValue().getWidthMm()));
                    assertEquals(0, new java.math.BigDecimal("1000000").compareTo(saved.getValue().getLengthMm()));
                }
            }
        }
    }

    @Test void fieldUnitsConvertMmWithDefaultsAndExplicitPrecision() {
        assertEquals("254μm", MaterialSizeValue.segment(new java.math.BigDecimal("0.254"), "μm", null).text());
        assertEquals("254.1μm", MaterialSizeValue.segment(new java.math.BigDecimal("0.2541"), "μm", null).text());
        assertEquals("1.01m", MaterialSizeValue.segment(new java.math.BigDecimal("1005"), "m", null).text());
        assertEquals("31.5cm", MaterialSizeValue.segment(new java.math.BigDecimal("315"), "cm", null).text());
        assertEquals("0.175mm", MaterialSizeValue.segment(new java.math.BigDecimal("0.1750"), "mm", null).text());
        assertEquals("0.18mm", MaterialSizeValue.segment(new java.math.BigDecimal("0.1750"), "mm", 2).text());
        assertEquals("254μm", MaterialSizeValue.segment(new java.math.BigDecimal("0.2541"), "μm", 0).text());
        assertEquals("0.175mm", MaterialSizeValue.render(null, null, new java.math.BigDecimal("0.175"), "mm"));
        assertThrows(RuntimeException.class, () -> MaterialSizeValue.segment(java.math.BigDecimal.ONE, "inch", null));
        assertThrows(RuntimeException.class, () -> MaterialSizeValue.segment(java.math.BigDecimal.ONE, "mm", 7));
    }

    @Test void importParserAcceptsMicronsAndKeepsOtherUnits() {
        for (String unit : new String[]{"μm", "µm", "um"}) {
            var parsed = com.jjx.inventory.util.MaterialSizeParser.parse("315mm*1000m*254" + unit);
            assertEquals(0, new java.math.BigDecimal("0.254").compareTo(parsed.thicknessMm()));
            assertEquals(0, new java.math.BigDecimal("1000000").compareTo(parsed.lengthMm()));
            assertEquals("315mm*1000m*254" + unit, com.jjx.inventory.util.MaterialSizeParser.extractLegacy("尺寸:315mm*1000m*254" + unit));
        }
        assertEquals(0, new java.math.BigDecimal("25.4").compareTo(com.jjx.inventory.util.MaterialSizeParser.parse("1in").widthMm()));
        assertEquals(0, new java.math.BigDecimal("10").compareTo(com.jjx.inventory.util.MaterialSizeParser.parse("1cm").widthMm()));
    }
    @Test void unifiedSizeKeepsUnitsPositionsAndLegacyValues() {
        assertEquals("300*305*0.175mm", MaterialSizeValue.mergeLegacy("300×305mm", "0.175"));
        assertEquals("0.175", MaterialSizeValue.thickness("914**0.175mm"));
        assertEquals("0.05", MaterialSizeValue.thickness("Φ8*0.05mm"));
        assertNull(MaterialSizeValue.thickness("30*90mm"));
        assertEquals("按图纸", MaterialSizeValue.mergeLegacy("按图纸", 0.175));
        assertEquals("300", MaterialSizeValue.mergeLegacy("300", "280g"));
        assertEquals("**0.175mm", MaterialSizeValue.mergeLegacy("", 0.175));
        assertThrows(RuntimeException.class, () -> MaterialSizeValue.validate("30*90mmmm"));
    }

    @Test void normalizeWritesHalfWidthAsteriskAndKeepsUnparsableText() {
        assertEquals("300*305", MaterialSizeValue.normalize("300×305"));
        assertEquals("300*305", MaterialSizeValue.normalize("300x305"));
        assertEquals("300*305", MaterialSizeValue.normalize("300X305"));
        assertEquals("300*305", MaterialSizeValue.normalize("300＊305"));
        assertEquals("300*305", MaterialSizeValue.normalize(" 300 * 305 "));
        assertEquals("Φ8*0.05mm", MaterialSizeValue.normalize("Φ8×0.05mm"));
        assertEquals("914**0.175mm", MaterialSizeValue.normalize("914××0.175mm"));
        assertEquals("300*305MM", MaterialSizeValue.normalize("300*305MM"));
        assertEquals("按图纸", MaterialSizeValue.normalize("按图纸"));
        assertEquals("30*90mmmm", MaterialSizeValue.normalize("30*90mmmm"));
        assertEquals(null, MaterialSizeValue.normalize(null));
    }
    @Test void numericDimensionsValidateStoragePrecisionAndRenderMissingPositions() {
        var w = new java.math.BigDecimal("300.00");
        var t = new java.math.BigDecimal("0.1750");
        assertEquals("300**0.175mm", MaterialSizeValue.render(w, null, t, "mm"));
        // 只填厚度合法：前导缺位不补占位（dev-20261030-016）
        assertEquals("0.175mm", MaterialSizeValue.render(null, null, t, "mm"));
        assertEquals("305*0.175mm", MaterialSizeValue.render(null, new java.math.BigDecimal("305"), t, "mm"));
        assertEquals("300cm", MaterialSizeValue.render(w, null, null, "cm"));
        assertNull(MaterialSizeValue.render(null, null, null, "mm"));
        assertEquals("cm", MaterialSizeValue.validate(w, null, null, " cm ").unit());
        for (String invalid : new String[]{"0", "-1", "1.001", "100000000"}) {
            assertThrows(RuntimeException.class, () -> MaterialSizeValue.validate(new java.math.BigDecimal(invalid), null, null, "mm"));
        }
        assertThrows(RuntimeException.class, () -> MaterialSizeValue.validate(w, null, t, "inch"));
        assertThrows(RuntimeException.class, () -> MaterialSizeValue.validate(w, null, new java.math.BigDecimal("0.00001"), "mm"));
    }
}
