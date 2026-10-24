package com.jjx.inventory;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.common.utils.ExcelUtils;
import com.jjx.framework.common.RedisSequenceService;
import com.jjx.inventory.converter.MaterialConverter;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.dto.imports.MaterialImportDTO;
import com.jjx.inventory.dto.query.MaterialQueryDTO;
import com.jjx.inventory.dto.update.MaterialUpdateDTO;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.inventory.service.impl.InventoryMaterialServiceImpl;
import com.jjx.inventory.util.MaterialSizeParser;
import com.jjx.system.service.ISysTagService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MaterialMasterDataTest {
    @Mock InventoryMaterialMapper mapper;
    @Mock RedisSequenceService sequence;
    @Mock ISysTagService tags;
    @Mock com.jjx.inventory.service.InventoryMaterialCategoryService categories;
    @InjectMocks InventoryMaterialServiceImpl service;

    @BeforeEach void setup() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), InventoryMaterial.class);
        ReflectionTestUtils.setField(service, "materialConverter", Mappers.getMapper(MaterialConverter.class));
    }

    static Map<String, Object> category() {
        return Map.of("category_id", 2L, "category_code", "RM-FILM", "category_path", "RAW/RAW_BASE/RM-FILM", "category_name", "片材/膜材");
    }

    static InventoryMaterial material() {
        InventoryMaterial m = new InventoryMaterial();
        m.setMaterialCode("TEST012"); m.setMaterialName("测试PC膜"); m.setCategoryId(2L);
        m.setMaterialGrade("PC膜"); m.setModel("F200"); m.setColor("透明"); m.setBrand("3M");
        m.setSize("0.3m×305×0.1"); m.setSpecification("中砂面"); m.setSearchKeywords("测试别名");
        return m;
    }

    @Test void unitsAndCategorySemantics() {
        var sheet = MaterialSizeParser.parse("0.3m×30.5cm×0.01inch", "RM-FILM");
        assertEquals(0, sheet.widthMm().compareTo(new BigDecimal("300")));
        assertEquals(0, sheet.lengthMm().compareTo(new BigDecimal("305")));
        assertEquals(0, sheet.thicknessMm().compareTo(new BigDecimal("0.254")));
        var roll = MaterialSizeParser.parse("300×1.0", "RM-ADHESIVE");
        assertNull(roll.lengthMm()); assertEquals(0, roll.thicknessMm().compareTo(BigDecimal.ONE));
        assertNull(MaterialSizeParser.parse("300", "RM-FILM").lengthMm());
        for (String invalid : List.of("300×", "-1", "0×30", "1×2×3×4", "300foo")) {
            assertThrows(IllegalArgumentException.class, () -> MaterialSizeParser.parse(invalid, "RM-FILM"));
        }
        assertEquals("300*305mm", MaterialSizeParser.extractLegacy("雾面300*305mm"));
        assertNull(MaterialSizeParser.extractLegacy("雾面"));
        assertNull(MaterialSizeParser.extractLegacy("M1.6*3"));
    }

    @Test void saveMapsAllFieldsAndUsesCategoryType() {
        when(mapper.selectCategory(2L)).thenReturn(category());
        when(mapper.selectRequiredFields("RM-FILM")).thenReturn(List.of("material_grade", "color", "specification"));
        when(mapper.insert(any(InventoryMaterial.class))).thenReturn(1);
        InventoryMaterial m = material();
        assertTrue(service.create(m, List.of(), "codex"));
        assertEquals("R", com.jjx.inventory.util.MaterialClassification.materialTypeOf(category()));
        var vo = Mappers.getMapper(MaterialConverter.class).toVO(m, Map.of(2L, "片材/膜材"));
        assertEquals(m.getMaterialGrade(), vo.getMaterialGrade()); assertEquals(m.getSize(), vo.getSize());
        assertEquals(m.getBrand(), vo.getBrand()); assertEquals(m.getSearchKeywords(), vo.getSearchKeywords());
    }

    @Test void attributeSaveDerivesColumnsAndConverterReturnsThem() {
        when(mapper.selectCategory(2L)).thenReturn(category());
        when(mapper.selectTemplateId(2L)).thenReturn(71L);
        when(mapper.insert(any(InventoryMaterial.class))).thenReturn(1);
        var m = material(); m.setSize("300×305");
        m.setTemplateId(999L); m.setThickness("伪造"); m.setWidthSize("伪造");
        m.setAttributeJson("{\"thickness_mm\":0.175}");
        assertTrue(service.create(m, List.of(), "codex"));
        assertEquals(71L, m.getTemplateId()); assertEquals("0.175", m.getThickness()); assertEquals("300×305", m.getWidthSize());
        var vo = Mappers.getMapper(MaterialConverter.class).toVO(m, Map.of());
        assertEquals(71L, vo.getTemplateId()); assertEquals("0.175", vo.getThickness()); assertEquals("300×305", vo.getWidthSize());
    }

    @Test void attributeEditClearsRemovedDerivedValuesButUnrelatedEditDoesNotWriteThem() {
        var old = material(); old.setMaterialId(12L); old.setThickness("0.175"); old.setWidthSize("300×305");
        when(mapper.selectById(12L)).thenReturn(old);
        when(mapper.selectCategory(2L)).thenReturn(category());
        when(mapper.selectTemplateId(2L)).thenReturn(71L);
        when(mapper.update(any(InventoryMaterial.class), any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(1);
        var patch = new InventoryMaterial(); patch.setMaterialId(12L); patch.setAttributeJson("{}");
        patch.setSuppliedFields(new java.util.HashSet<>(List.of("attributeJson", "size")));
        assertTrue(service.update(patch, null, "codex"));
        var wrapper = ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class);
        verify(mapper).update(eq(patch), wrapper.capture());
        assertTrue(wrapper.getValue().getSqlSet().contains("thickness="));
        assertTrue(wrapper.getValue().getSqlSet().contains("width_size="));
        assertNull(patch.getThickness()); assertNull(patch.getWidthSize());
        clearInvocations(mapper);
        var brandOnly = new InventoryMaterial(); brandOnly.setMaterialId(12L); brandOnly.setBrand("新品牌");
        brandOnly.setTemplateId(999L); brandOnly.setThickness("独立覆盖");
        assertTrue(service.update(brandOnly, null, "codex"));
        assertNull(brandOnly.getTemplateId()); assertNull(brandOnly.getThickness());
        verify(mapper, never()).selectTemplateId(anyLong());
    }

    @Test void masterPreparationUsesCategoryTemplateAndWritesJsonMaterialMirror() {
        var catalog = mock(com.jjx.inventory.master.MaterialMasterCatalog.class);
        var template = new com.jjx.inventory.master.InventoryAttributeTemplate(); template.setTemplateId(71L);
        var category = new com.jjx.inventory.master.MaterialMasterCatalog.Category(2L,"RAW_BASE_PC","PC",1L,3,"R","原材料/基材/PC",true,1,1);
        var schema = com.jjx.inventory.master.MaterialAttributeSchema.parse(
                "{\"version\":1,\"meta_options\":[\"PC\"],\"default_meta\":\"PC\",\"default_unit\":\"M\",\"fields\":[{\"key\":\"thickness_mm\",\"label\":\"厚度\",\"type\":\"decimal\"}]}", new ObjectMapper());
        when(catalog.configuration(2L)).thenReturn(new com.jjx.inventory.master.MaterialMasterCatalog.Configuration(category, List.of(category), template, schema));
        when(catalog.dictionaries()).thenReturn(Map.of("material_meta",List.of(new com.jjx.inventory.master.MaterialMasterCatalog.DictionaryOption("PC","PC")),
                "unit",List.of(new com.jjx.inventory.master.MaterialMasterCatalog.DictionaryOption("M","米"))));
        var master = new com.jjx.inventory.master.MaterialMasterService(catalog, mock(com.jjx.inventory.master.MaterialMasterMapper.class), mapper,
                service, mock(com.jjx.purchase.mapper.PurchaseSupplierMapper.class), tags, mock(com.jjx.event.EventPublisher.class), new ObjectMapper());
        var dto = new com.jjx.inventory.master.MaterialMasterDTO(); dto.setCategoryId(2L); dto.setOriginalName("PC样例");
        dto.setSize("300×305"); dto.setTemplateId(999L); dto.setThickness("伪造"); dto.setAttributeJson(Map.of("thickness_mm", new BigDecimal("0.175")));
        var saved = master.prepare(dto).material();
        assertEquals(71L, saved.getTemplateId()); assertEquals("0.175", saved.getThickness()); assertEquals("300×305", saved.getWidthSize());
        assertTrue(saved.getAttributeJson().contains("\"material\":\"PC\""));
        assertFalse(master.readAttributes(saved.getAttributeJson()).containsKey("material"));
        dto.setAttributeJson(Map.of()); dto.setSize(null);
        var cleared = master.prepare(dto).material(); assertNull(cleared.getThickness()); assertNull(cleared.getWidthSize());
    }

    @Test void derivedFieldsIgnoreClientInputAndLegacySpecWritesAreDisabled() throws Exception {
        var dto = new ObjectMapper().readValue("{\"templateId\":999,\"thickness\":\"fake\",\"widthSize\":\"fake\"}", MaterialUpdateDTO.class);
        assertNull(dto.getTemplateId()); assertNull(dto.getThickness()); assertNull(dto.getWidthSize());
        var adapter = new com.jjx.inventory.service.impl.InventoryMaterialSpecServiceImpl(mapper,
                mock(com.jjx.inventory.mapper.InventoryMaterialCategoryMapper.class), new ObjectMapper());
        assertTrue(adapter.listDefs("RM-FILM").isEmpty());
        assertTrue(assertThrows(BusinessException.class, () -> adapter.save(new com.jjx.inventory.dto.update.MaterialSpecValuesDTO())).getMessage().contains("已停用"));
        var m = material(); m.setCategoryId(null); m.setAttributeJson("{\"thickness_mm\":0.175}");
        when(mapper.selectById(12L)).thenReturn(m);
        assertEquals("0.175", adapter.getByMaterialId(12L).getValues().get(0).getValueText());
        verify(mapper, never()).insert(any(InventoryMaterial.class));
    }

    @Test void missingRequiredRejectedWithFieldName() {
        when(mapper.selectCategory(2L)).thenReturn(category());
        when(mapper.selectRequiredFields("RM-FILM")).thenReturn(List.of("color"));
        InventoryMaterial m = material(); m.setColor(null);
        assertTrue(assertThrows(BusinessException.class, () -> service.create(m, List.of(), "codex")).getMessage().contains("颜色"));
        verify(mapper, never()).insert(any(InventoryMaterial.class));
    }

    @Test void legacyEditDoesNotValidateMissingHistoricalFieldsButRejectsClearing() throws Exception {
        InventoryMaterial old = material(); old.setMaterialId(12L); old.setColor(null); old.setSpecification(null);
        when(mapper.selectById(12L)).thenReturn(old);
        when(mapper.selectCategory(2L)).thenReturn(category());
        when(mapper.selectRequiredFields("RM-FILM")).thenReturn(List.of("color", "specification", "material_grade"));
        when(mapper.update(any(InventoryMaterial.class), any())).thenReturn(1);
        InventoryMaterial patch = new InventoryMaterial(); patch.setMaterialId(12L); patch.setBrand("新品牌");
        assertTrue(service.update(patch, null, "codex"));
        MaterialUpdateDTO dto = new ObjectMapper().readValue("{\"materialId\":12,\"materialGrade\":null}", MaterialUpdateDTO.class);
        InventoryMaterial clear = new InventoryMaterial(); BeanUtils.copyProperties(dto, clear);
        assertTrue(assertThrows(BusinessException.class, () -> service.update(clear, null, "codex")).getMessage().contains("材料"));
    }

    @Test void listAndSearchUseAttributesAndKeepTokenSearch() {
        when(mapper.selectList(any())).thenReturn(List.of());
        MaterialQueryDTO dto = new MaterialQueryDTO(); dto.setCategoryId(2L); dto.setMaterialGrade("PC膜");
        dto.setColor("透明"); dto.setModel("F200"); dto.setBrand("3M"); dto.setKeyword("f200 320");
        service.selectEntities(dto);
        var wrapper = ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        verify(mapper).selectList(wrapper.capture());
        String sql = wrapper.getValue().getSqlSegment();
        for (String column : List.of("category_id", "material_grade", "model", "color", "brand")) assertTrue(sql.contains(column));
        service.search(dto);
        verify(mapper).selectPage(any(), wrapper.capture());
        sql = wrapper.getValue().getSqlSegment();
        assertEquals(2, sql.split("search_blob LIKE", -1).length - 1);
        assertTrue(wrapper.getValue().getCustomSqlSegment().contains("brand"));
    }

    static List<MaterialImportDTO> workbook(String[] headers, String[] values) throws Exception {
        try (var wb = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var sheet = wb.createSheet(); var head = sheet.createRow(0); var row = sheet.createRow(1);
            for (int i = 0; i < headers.length; i++) { head.createCell(i).setCellValue(headers[i]); row.createCell(i).setCellValue(values[i]); }
            wb.write(out);
            return ExcelUtils.importExcel(new MockMultipartFile("file", "test.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray()), MaterialImportDTO.class);
        }
    }

    @Test void newAndOldExcelHeadersBothImport() throws Exception {
        when(mapper.findCategory(anyString())).thenReturn(category());
        when(mapper.selectCategory(2L)).thenReturn(category());
        when(mapper.selectRequiredFields("RM-FILM")).thenReturn(List.of("material_grade", "color", "specification"));
        when(sequence.generateBusinessNumberByTypeWithPrefix(anyString(), anyString(), anyString(), anyInt(), anyLong())).thenReturn("TEST012");
        var modern = workbook(new String[]{"物料名称", "品类", "材料", "型号", "颜色", "品牌", "尺寸", "规格"},
                new String[]{"新模板PC膜", "RM-FILM", "PC膜", "F200", "透明", "3M", "300×305", "雾面"});
        assertEquals(1, service.importMaterial(modern, "codex").getSuccessCount());
        var legacy = workbook(new String[]{"材料", "规格"}, new String[]{"PC透明旧模板", "雾面300*305mm"});
        assertEquals(1, service.importMaterial(legacy, "codex").getSuccessCount());
        var inserted = ArgumentCaptor.forClass(InventoryMaterial.class);
        verify(mapper, times(2)).insert(inserted.capture());
        assertEquals("PC膜", inserted.getAllValues().get(1).getMaterialGrade());
        assertEquals("透明", inserted.getAllValues().get(1).getColor());
        assertEquals("300*305mm", inserted.getAllValues().get(1).getSize());
        assertEquals("雾面300*305mm", inserted.getAllValues().get(1).getSpecification());
    }
    @Test void legacyBrandAndNumericModelCanSatisfyAdhesiveRules() throws Exception {
        var adhesive = Map.<String, Object>of("category_id", 5L, "category_code", "AUX-ADHESIVE", "category_path", "RAW/RAW_AUX/AUX-ADHESIVE");
        when(mapper.findCategory("AUX-ADHESIVE")).thenReturn(adhesive);
        when(mapper.selectCategory(5L)).thenReturn(adhesive);
        when(mapper.selectRequiredFields("AUX-ADHESIVE")).thenReturn(List.of("model", "brand", "specification"));
        when(sequence.generateBusinessNumberByTypeWithPrefix(anyString(), anyString(), anyString(), anyInt(), anyLong())).thenReturn("TEST012");
        var legacy = workbook(new String[]{"材料", "规格"}, new String[]{"3M5604", "300×1.0"});
        assertEquals(1, service.importMaterial(legacy, "codex").getSuccessCount());
        var inserted = ArgumentCaptor.forClass(InventoryMaterial.class);
        verify(mapper).insert(inserted.capture());
        assertEquals("5604", inserted.getValue().getModel());
        assertEquals("3M", inserted.getValue().getBrand());
        assertEquals("胶带", inserted.getValue().getMaterialGrade());
    }

    @Test void listMapsCategoryNamesInOneBatch() {
        var first = material(); first.setMaterialId(1L);
        var second = material(); second.setMaterialId(2L);
        var category = new com.jjx.inventory.domain.InventoryMaterialCategory();
        category.setCategoryId(2L); category.setCategoryName("片材/膜材");
        when(mapper.selectList(any())).thenReturn(List.of(first, second));
        when(categories.listByIds(List.of(2L))).thenReturn(List.of(category));
        var list = service.selectList(new MaterialQueryDTO());
        assertEquals(List.of("片材/膜材", "片材/膜材"), list.stream().map(com.jjx.inventory.dto.vo.MaterialVO::getCategoryName).toList());
        verify(categories, times(1)).listByIds(List.of(2L));
        verify(mapper, never()).selectCategory(anyLong());
    }

    @Test void categoryOptionsRequireLoginWithoutPermissionPoint() throws Exception {
        var method = com.jjx.inventory.controller.InventoryMaterialController.class.getMethod("categoryOptions");
        assertNotNull(method.getAnnotation(cn.dev33.satoken.annotation.SaCheckLogin.class));
        assertNull(method.getAnnotation(cn.dev33.satoken.annotation.SaCheckPermission.class));
        when(categories.listMaps(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of(category()));
        var options = service.getCategoryOptions();
        assertEquals(2L, options.get(0).getCategoryId());
        assertEquals("R", options.get(0).getRootType());
        assertEquals("片材/膜材", options.get(0).getCategoryName());
        var wrapper = ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        verify(categories).listMaps(wrapper.capture());
        assertTrue(wrapper.getValue().getSqlSegment().contains("status"));
    }

    @Test void importComposesNameFromStructuredFieldsWhenNameBlank() throws Exception {
        when(mapper.findCategory(anyString())).thenReturn(category());
        when(mapper.selectCategory(2L)).thenReturn(category());
        when(mapper.selectRequiredFields("RM-FILM")).thenReturn(List.of("material_grade", "color", "specification"));
        when(sequence.generateBusinessNumberByTypeWithPrefix(anyString(), anyString(), anyString(), anyInt(), anyLong())).thenReturn("TEST012");
        var rows = workbook(new String[]{"品类", "材料", "型号", "颜色", "品牌", "尺寸", "规格"},
                new String[]{"RM-FILM", "PC膜", "F200", "透明", "3M", "300×305", "雾面"});
        var result = service.importMaterial(rows, "codex");
        assertEquals(1, result.getSuccessCount(), () -> result.getFailDetails().toString());
        var inserted = ArgumentCaptor.forClass(InventoryMaterial.class);
        verify(mapper).insert(inserted.capture());
        assertEquals("PC膜F200透明（3M）", inserted.getValue().getMaterialName());
    }

    @Test void importKeepsManualNameAndLegacyName() throws Exception {
        when(mapper.findCategory(anyString())).thenReturn(category());
        when(mapper.selectCategory(2L)).thenReturn(category());
        when(mapper.selectRequiredFields("RM-FILM")).thenReturn(List.of("material_grade", "color", "specification"));
        when(sequence.generateBusinessNumberByTypeWithPrefix(anyString(), anyString(), anyString(), anyInt(), anyLong())).thenReturn("TEST012");
        var manual = workbook(new String[]{"物料名称", "品类", "材料", "型号", "颜色", "规格"},
                new String[]{"人工命名A", "RM-FILM", "PC膜", "F200", "透明", "雾面"});
        assertEquals(1, service.importMaterial(manual, "codex").getSuccessCount());
        var legacy = workbook(new String[]{"材料", "规格"}, new String[]{"PC透明旧模板X", "雾面300*305mm"});
        assertEquals(1, service.importMaterial(legacy, "codex").getSuccessCount());
        var inserted = ArgumentCaptor.forClass(InventoryMaterial.class);
        verify(mapper, times(2)).insert(inserted.capture());
        assertEquals("人工命名A", inserted.getAllValues().get(0).getMaterialName());
        assertEquals("PC透明旧模板X", inserted.getAllValues().get(1).getMaterialName());
    }

    @Test void importPrecheckReportsMissingColumn() throws Exception {
        when(mapper.findCategory(anyString())).thenReturn(category());
        when(mapper.selectRequiredFields("RM-FILM")).thenReturn(List.of("material_grade", "color", "specification"));
        var rows = workbook(new String[]{"物料名称", "品类", "材料", "规格"},
                new String[]{"缺颜色物料", "RM-FILM", "PC膜", "雾面"});
        var result = service.importMaterial(rows, "codex");
        assertEquals(0, result.getSuccessCount());
        assertEquals(1, result.getFailCount());
        String reason = result.getFailDetails().get(0).getReason();
        assertTrue(reason.contains("缺【颜色】列"), reason);
        assertTrue(reason.contains("RM-FILM"), reason);
        verify(mapper, never()).insert(any(InventoryMaterial.class));
    }

    @Test void importSkipsExistingByAttributesInsteadOfName() throws Exception {
        when(mapper.findCategory(anyString())).thenReturn(category());
        when(mapper.selectRequiredFields("RM-FILM")).thenReturn(List.of("material_grade", "color", "specification"));
        when(mapper.selectCount(any())).thenReturn(1L);
        var rows = workbook(new String[]{"物料名称", "品类", "材料", "型号", "颜色", "尺寸", "规格"},
                new String[]{"换了个写法", "RM-FILM", "PC膜", "F200", "透明", "300×305", "雾面"});
        var result = service.importMaterial(rows, "codex");
        assertEquals(1, result.getSkipCount());
        assertEquals(0, result.getSuccessCount());
        verify(mapper, never()).insert(any(InventoryMaterial.class));
        var wrapper = ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        verify(mapper).selectCount(wrapper.capture());
        String sql = wrapper.getValue().getSqlSegment();
        for (String column : List.of("category_id", "material_grade", "model", "color", "brand", "size", "specification")) {
            assertTrue(sql.contains("IFNULL(" + column), sql);
        }
        assertFalse(sql.contains("material_name"), sql);
    }

    @Test void importRejectsDuplicateRowsInFile() throws Exception {
        when(mapper.findCategory(anyString())).thenReturn(category());
        when(mapper.selectRequiredFields("RM-FILM")).thenReturn(List.of("material_grade", "color", "specification"));
        try (var wb = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var sheet = wb.createSheet();
            var head = sheet.createRow(0);
            String[] headers = {"物料名称", "品类", "材料", "型号", "颜色", "尺寸", "规格"};
            for (int i = 0; i < headers.length; i++) head.createCell(i).setCellValue(headers[i]);
            for (int r = 1; r <= 2; r++) {
                var row = sheet.createRow(r);
                String[] values = {"写法" + r, "RM-FILM", "PC膜", "F200", "透明", "300×305", "雾面"};
                for (int i = 0; i < values.length; i++) row.createCell(i).setCellValue(values[i]);
            }
            wb.write(out);
            var list = ExcelUtils.importExcel(new MockMultipartFile("file", "test.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray()), MaterialImportDTO.class);
            var result = service.importMaterial(list, "codex");
            assertEquals(0, result.getSuccessCount());
            assertEquals(2, result.getFailCount());
            assertTrue(result.getFailDetails().get(0).getReason().contains("文件内重复行"));
        }
        verify(mapper, never()).insert(any(InventoryMaterial.class));
    }

    @Test void importNewColumnsLandAndStockQuantityColumnIgnored() throws Exception {
        when(mapper.findCategory(anyString())).thenReturn(category());
        when(mapper.selectCategory(2L)).thenReturn(category());
        when(mapper.selectRequiredFields("RM-FILM")).thenReturn(List.of("material_grade", "color", "specification"));
        when(sequence.generateBusinessNumberByTypeWithPrefix(anyString(), anyString(), anyString(), anyInt(), anyLong())).thenReturn("TEST012");
        var rows = workbook(
                new String[]{"物料名称", "品类", "材料", "颜色", "规格", "别名", "标准单价", "采购周期(天)",
                        "安全库存", "最高库存", "再订货点", "保质期(天)", "预警提前天数", "批次控制", "数量"},
                new String[]{"新字段物料", "RM-FILM", "PC膜", "透明", "雾面", "老料号A", "12.5", "7",
                        "5", "100", "3", "365", "15", "是", "999"});
        var result = service.importMaterial(rows, "codex");
        assertEquals(1, result.getSuccessCount(), () -> result.getFailDetails().toString());
        var inserted = ArgumentCaptor.forClass(InventoryMaterial.class);
        verify(mapper).insert(inserted.capture());
        InventoryMaterial saved = inserted.getValue();
        assertEquals("老料号A", saved.getSearchKeywords());
        assertEquals(0, saved.getStandardPrice().compareTo(new BigDecimal("12.5")));
        assertEquals(7, saved.getLeadTime());
        assertEquals(0, saved.getSafeStock().compareTo(new BigDecimal("5")));
        assertEquals(0, saved.getMaxStock().compareTo(new BigDecimal("100")));
        assertEquals(0, saved.getReorderPoint().compareTo(new BigDecimal("3")));
        assertEquals(365, saved.getShelfLife());
        assertEquals(15, saved.getExpiryAlertDays());
        assertEquals(Boolean.TRUE, saved.getBatchControl());
        // 「数量」列已从模板删除：旧文件里的数量列不写库存（导入不碰库存数量）
        assertTrue(Arrays.stream(MaterialImportDTO.class.getDeclaredFields())
                .noneMatch(field -> "currentStock".equals(field.getName())));
    }

    @Test void keywordSearchMatchesCategoryNamePathToo() {
        when(mapper.selectList(any())).thenReturn(List.of());
        MaterialQueryDTO dto = new MaterialQueryDTO();
        dto.setKeyword("保护膜");
        service.selectEntities(dto);
        var wrapper = ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        verify(mapper).selectList(wrapper.capture());
        String sql = wrapper.getValue().getSqlSegment();
        assertTrue(sql.contains("search_blob LIKE"), sql);
        assertTrue(sql.contains("inventory_material_category"), sql);
        assertTrue(sql.contains("name_path LIKE"), sql);
    }

    @Test void classificationUsesRootAndSecondLevelWithoutLegacyTypesOrDefaults() {
        var types = Map.of("RAW/RAW_BASE/RAW_BASE_PC", "R", "RAW/RAW_AUX/RAW_AUX_OTHER", "R",
                "RAW/RAW_INK", "R", "SEMI", "S", "FINISHED", "F");
        var prefixes = Map.of("RAW/RAW_BASE/RAW_BASE_PC", "RM", "RAW/RAW_AUX/RAW_AUX_OTHER", "AUX",
                "RAW/RAW_INK", "INK", "SEMI", "SFG", "FINISHED", "FG");
        types.forEach((path, type) -> {
            var category = Map.<String,Object>of("category_path", path, "material_type", "A");
            assertEquals(type, com.jjx.inventory.util.MaterialClassification.materialTypeOf(category));
            assertEquals(prefixes.get(path), com.jjx.inventory.util.MaterialClassification.codePrefixOf(category));
        });
        assertNull(com.jjx.inventory.util.MaterialClassification.materialTypeOf(null));
        assertNull(com.jjx.inventory.util.MaterialClassification.materialTypeOf(Map.of("material_type", "R")));
        assertThrows(BusinessException.class, () -> service.generateCode(null));
        verifyNoInteractions(sequence);
    }

    @Test void unclassifiedExistingMaterialCanBeEditedWithoutInventingAType() {
        InventoryMaterial existing = new InventoryMaterial(); existing.setMaterialId(900L); existing.setMaterialCode("AUX000123");
        when(mapper.selectById(900L)).thenReturn(existing);
        when(mapper.update(any(InventoryMaterial.class), any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(1);
        InventoryMaterial patch = new InventoryMaterial(); patch.setMaterialId(900L); patch.setBrand("人工品牌");
        assertTrue(service.update(patch, null, "codex"));
        verify(mapper, never()).selectCategory(anyLong());
        assertEquals("AUX000123", existing.getMaterialCode());
    }

    @Test void legacyPayloadFieldsAreIgnoredByBothSaveEntrypoints() throws Exception {
        String json = "{\"categoryId\":18,\"materialType\":\"A\",\"materialNameEn\":\"old\","
                + "\"materialCategory\":\"old\",\"specSupplement\":\"old\",\"costPriceFrom\":\"old\",\"costPriceTime\":\"old\"}";
        var mapper = new ObjectMapper();
        assertEquals(18L, mapper.readValue(json, com.jjx.inventory.dto.save.MaterialSaveDTO.class).getCategoryId());
        assertEquals(18L, mapper.readValue(json, MaterialUpdateDTO.class).getCategoryId());
        assertEquals(18L, mapper.readValue(json, com.jjx.inventory.master.MaterialMasterDTO.class).getCategoryId());
        for (var field : TableInfoHelper.getTableInfo(InventoryMaterial.class).getFieldList()) {
            assertFalse(List.of("material_type","material_category","material_name_en","spec_supplement","cost_price_from","cost_price_time").contains(field.getColumn()));
        }
    }

    @Test void rootFilterUsesClassificationAndRejectsOldAuxiliaryType() {
        var query = new MaterialQueryDTO(); query.setRootType("R");
        service.selectEntities(query);
        var wrapper = ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        verify(mapper).selectList(wrapper.capture());
        String sql = wrapper.getValue().getSqlSegment();
        assertTrue(sql.contains("category_path")); assertFalse(sql.contains("material_type"));
        query.setRootType("A"); assertThrows(BusinessException.class, () -> service.selectEntities(query));
    }
    @Test void classStatusFiltersAreSharedByListSearchAndPaging() {
        when(tags.resolveBizIdsFilter(anyString(), any(), any())).thenReturn(null);
        when(mapper.selectList(any())).thenReturn(List.of());
        var query = new MaterialQueryDTO();
        var wrapper = ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        query.setClassStatus("unclassified"); service.selectEntities(query);
        verify(mapper).selectList(wrapper.capture());
        assertTrue(wrapper.getValue().getSqlSegment().contains("category_id IS NULL"));
        query.setClassStatus("rule_unconfirmed"); service.search(query);
        verify(mapper).selectPage(any(), wrapper.capture());
        String sql = wrapper.getValue().getSqlSegment();
        assertTrue(sql.contains("class_source =")); assertTrue(sql.contains("class_confirmed ="));
        assertTrue(((com.baomidou.mybatisplus.core.conditions.AbstractWrapper)wrapper.getValue()).getParamNameValuePairs().values().contains("RULE"));
        assertTrue(((com.baomidou.mybatisplus.core.conditions.AbstractWrapper)wrapper.getValue()).getParamNameValuePairs().values().contains(false));
        clearInvocations(mapper);
        query.setClassStatus("confirmed"); service.pageQuery(query);
        verify(mapper).selectPage(any(), wrapper.capture());
        assertTrue(wrapper.getValue().getSqlSegment().contains("class_confirmed ="));
        assertTrue(((com.baomidou.mybatisplus.core.conditions.AbstractWrapper)wrapper.getValue()).getParamNameValuePairs().values().contains(true));
    }

    @Test void noFilterAndUnknownClassStatusKeepAllMaterialsWithNullOriginalNames() {
        when(tags.resolveBizIdsFilter(anyString(), any(), any())).thenReturn(null);
        InventoryMaterial unclassified = new InventoryMaterial();
        unclassified.setMaterialId(1L);
        InventoryMaterial rule = new InventoryMaterial();
        rule.setMaterialId(2L); rule.setClassSource("RULE"); rule.setClassConfirmed(false);
        InventoryMaterial confirmed = new InventoryMaterial();
        confirmed.setMaterialId(3L); confirmed.setClassConfirmed(true);
        List<InventoryMaterial> all = List.of(unclassified, rule, confirmed);
        assertTrue(all.stream().allMatch(m -> m.getOriginalName() == null));
        when(mapper.selectList(any())).thenReturn(all);
        var query = new MaterialQueryDTO();
        for (String classStatus : Arrays.asList(null, "", "bogus")) {
            for (Boolean legacyFlag : Arrays.asList(null, true, false)) {
                clearInvocations(mapper);
                query.setClassStatus(classStatus); query.setMasterDataOnly(legacyFlag);
                assertEquals(all, service.selectEntities(query));
                var wrapper = ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
                verify(mapper).selectList(wrapper.capture());
                // 无 WHERE 条件，分页/排序口径不变；兼容旧客户端携带的 masterDataOnly。
                assertEquals("ORDER BY create_time DESC,material_id DESC", wrapper.getValue().getSqlSegment().trim());
            }
        }
        query.setClassStatus("bogus");
        assertEquals(3, service.selectList(query).size());
        assertDoesNotThrow(() -> service.pageQuery(query));
        assertDoesNotThrow(() -> service.search(query));
    }

    @Test void masterExportUsesTheSameQueryWithoutInjectingLegacyScope() {
        var query = new MaterialQueryDTO(); query.setClassStatus("confirmed");
        var master = new com.jjx.inventory.master.MaterialMasterService(null, null, mapper,
                service, null, null, null, new ObjectMapper());
        master.selectForExport(query);
        assertNull(query.getMasterDataOnly());
        assertEquals("confirmed", query.getClassStatus());
        var wrapper = ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        verify(mapper).selectList(wrapper.capture());
        assertTrue(wrapper.getValue().getSqlSegment().contains("class_confirmed ="));
        assertFalse(wrapper.getValue().getSqlSegment().contains("original_name"));
    }

}
