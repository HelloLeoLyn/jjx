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
        return Map.of("category_id", 2L, "category_code", "RM-FILM", "material_type", "R", "category_name", "片材/膜材");
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
        InventoryMaterial m = material(); m.setMaterialType("A");
        assertTrue(service.create(m, List.of(), "codex"));
        assertEquals("R", m.getMaterialType());
        var vo = Mappers.getMapper(MaterialConverter.class).toVO(m, Map.of(2L, "片材/膜材"));
        assertEquals(m.getMaterialGrade(), vo.getMaterialGrade()); assertEquals(m.getSize(), vo.getSize());
        assertEquals(m.getBrand(), vo.getBrand()); assertEquals(m.getSearchKeywords(), vo.getSearchKeywords());
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
        var adhesive = Map.<String, Object>of("category_id", 5L, "category_code", "AUX-ADHESIVE", "material_type", "A");
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
        assertEquals("R", options.get(0).getMaterialType());
        assertEquals("片材/膜材", options.get(0).getCategoryName());
        var wrapper = ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.Wrapper.class);
        verify(categories).listMaps(wrapper.capture());
        assertTrue(wrapper.getValue().getSqlSegment().contains("status"));
    }

    /** 显式启用时使用真实业务库；整段事务回滚，不留测试物料、不调用库存路径。 */
    @Test
    @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "JJX_MATERIAL_DB_TEST", matches = "true")
    void realDatabaseSaveFilterImportAndRollback() throws Exception {
        var dataSource = new org.springframework.jdbc.datasource.DriverManagerDataSource(
                "jdbc:mysql://127.0.0.1:3306/jjx_erp_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai",
                System.getenv("JJX_MATERIAL_DB_USER"), System.getenv("JJX_MATERIAL_DB_PASSWORD"));
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new org.apache.ibatis.mapping.Environment("material-test",
                new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(), dataSource));
        com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils.setGlobalConfig(configuration,
                new com.baomidou.mybatisplus.core.config.GlobalConfig()
                    .setDbConfig(new com.baomidou.mybatisplus.core.config.GlobalConfig.DbConfig()).setMetaObjectHandler(
                    new com.baomidou.mybatisplus.core.handlers.MetaObjectHandler() {
                        public void insertFill(org.apache.ibatis.reflection.MetaObject meta) {
                            strictInsertFill(meta, "createBy", String.class, "codex-test");
                            strictInsertFill(meta, "updateBy", String.class, "codex-test");
                            strictInsertFill(meta, "createTime", java.time.LocalDateTime.class, java.time.LocalDateTime.now());
                            strictInsertFill(meta, "updateTime", java.time.LocalDateTime.class, java.time.LocalDateTime.now());
                        }
                        public void updateFill(org.apache.ibatis.reflection.MetaObject meta) {
                            setFieldValByName("updateBy", "codex-test", meta);
                            setFieldValByName("updateTime", java.time.LocalDateTime.now(), meta);
                        }
                    }));
        configuration.addInterceptor(new com.jjx.framework.config.MyBatisPlusConfig().mybatisPlusInterceptor());
        configuration.addMapper(InventoryMaterialMapper.class);
        configuration.addMapper(com.jjx.inventory.mapper.InventoryMaterialCategoryMapper.class);
        var factory = new com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder().build(configuration);
        String suffix = Long.toString(System.nanoTime());
        try (var session = factory.openSession(false)) {
            InventoryMaterialMapper realMapper = session.getMapper(InventoryMaterialMapper.class);
            ReflectionTestUtils.setField(service, "materialMapper", realMapper);
            var categoryMapper = session.getMapper(com.jjx.inventory.mapper.InventoryMaterialCategoryMapper.class);
            when(categories.listByIds(anyCollection())).thenAnswer(call -> categoryMapper.selectByIds(call.getArgument(0)));
            when(categories.listMaps(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenAnswer(call -> categoryMapper.selectMaps(call.getArgument(0)));
            when(tags.resolveBizIdsFilter(eq("inventory_material"), isNull(), isNull())).thenReturn(null);
            try {
                assertEquals(11, service.getCategoryOptions().size());
                for (String type : List.of("R", "A")) {
                    var option = service.getCategoryOptions().stream()
                            .filter(c -> c.getCategoryCode().equals("OTHER-" + type)).findFirst().orElseThrow();
                    InventoryMaterial batch = new InventoryMaterial();
                    batch.setMaterialCode("TEST015-" + type + "-" + suffix);
                    batch.setMaterialName("批量建档验证" + type);
                    batch.setCategoryId(option.getCategoryId()); batch.setMaterialType(type); batch.setUnit("PCS");
                    assertTrue(service.create(batch, List.of(), "codex"));
                    session.clearCache();
                    assertEquals(option.getCategoryId(), realMapper.selectById(batch.getMaterialId()).getCategoryId());
                    MaterialQueryDTO batchQuery = new MaterialQueryDTO(); batchQuery.setMaterialCode(batch.getMaterialCode());
                    var list = service.selectList(batchQuery);
                    assertEquals(1, list.size());
                    assertEquals(option.getCategoryName(), list.get(0).getCategoryName());
                    assertEquals(option.getCategoryName(), service.getDetailById(batch.getMaterialId()).getCategoryName());
                    System.out.println("REAL_DB_015 code=" + batch.getMaterialCode() + " categoryId="
                            + list.get(0).getCategoryId() + " categoryName=" + list.get(0).getCategoryName());
                }
                InventoryMaterial saved = material(); saved.setMaterialCode("TEST012-" + suffix); saved.setStatus(com.jjx.common.enums.StatusEnum.NORMAL.getCode()); saved.setUnit("PCS");
                assertTrue(service.create(saved, List.of(), "codex"));
                session.clearCache();
                InventoryMaterial read = realMapper.selectById(saved.getMaterialId());
                assertEquals(saved.getMaterialGrade(), read.getMaterialGrade());
                assertEquals(saved.getSize(), read.getSize()); assertEquals("F200", read.getModel());
                assertEquals("透明", read.getColor()); assertEquals("3M", read.getBrand());
                assertEquals("测试别名", read.getSearchKeywords()); assertEquals("R", read.getMaterialType());
                MaterialQueryDTO query = new MaterialQueryDTO(); query.setCategoryId(2L); query.setMaterialGrade("PC膜");
                query.setColor("透明"); query.setBrand("3M"); query.setModel("F200"); query.setMaterialCode(saved.getMaterialCode());
                assertEquals(1, service.selectEntities(query).size());
                query.setColor("不匹配"); assertEquals(0, service.selectEntities(query).size());
                query.setColor("透明"); query.setKeyword("F200 " + suffix);
                assertEquals(1, service.search(query).getTotal());
                assertEquals(1, service.pageQuery(query).getTotal());
                InventoryMaterial patch = new InventoryMaterial(); patch.setMaterialId(saved.getMaterialId()); patch.setBrand("新品牌");
                assertTrue(service.update(patch, null, "codex"));
                assertEquals("新品牌", realMapper.selectById(saved.getMaterialId()).getBrand());
                InventoryMaterial legacy = new InventoryMaterial(); legacy.setMaterialCode("TEST012-old-" + suffix);
                legacy.setMaterialName("历史缺字段"); legacy.setCategoryId(2L); legacy.setMaterialType("R"); legacy.setUnit("PCS");
                realMapper.insert(legacy);
                patch = new InventoryMaterial(); patch.setMaterialId(legacy.getMaterialId()); patch.setBrand("只改品牌");
                assertTrue(service.update(patch, null, "codex"));
                when(sequence.generateBusinessNumberByTypeWithPrefix(anyString(), anyString(), anyString(), anyInt(), anyLong()))
                        .thenReturn("TEST012-new-import-" + suffix, "TEST012-old-import-" + suffix);
                var modern = workbook(new String[]{"物料名称", "品类", "材料", "型号", "颜色", "品牌", "尺寸", "规格", "单位"},
                        new String[]{"新模板" + suffix, "RM-FILM", "PC膜", "F200", "透明", "3M", "300×305", "雾面", "PCS"});
                assertEquals(1, service.importMaterial(modern, "codex").getSuccessCount());
                var old = workbook(new String[]{"材料", "规格", "单位"}, new String[]{"PC透明旧模板" + suffix, "雾面300*305mm", "PCS"});
                assertEquals(1, service.importMaterial(old, "codex").getSuccessCount());
                InventoryMaterial imported = realMapper.selectByCode("TEST012-old-import-" + suffix);
                assertEquals("PC膜", imported.getMaterialGrade()); assertEquals("透明", imported.getColor());
                assertEquals("300*305mm", imported.getSize());
                // 在真正的生成列上验证多词LIKE仍同时命中，且包含新增尺寸和品牌。
                try (var statement = session.getConnection().prepareStatement(
                        "SELECT COUNT(*) FROM inventory_material WHERE material_code=? AND search_blob LIKE ? AND search_blob LIKE ?")) {
                    statement.setString(1, "TEST012-new-import-" + suffix); statement.setString(2, "%300%"); statement.setString(3, "%3M%");
                    try (var rows = statement.executeQuery()) { rows.next(); assertEquals(1, rows.getInt(1)); }
                }
                System.out.println("REAL_DB_EVIDENCE saved_id=" + saved.getMaterialId()
                        + " category=2 type=R grade=PC膜 model=F200 color=透明 size=0.3m×305×0.1 keywords=测试别名"
                        + " filters_match=1 filters_mismatch=0 legacy_edit=PASS new_excel=1 old_excel=1 search_blob=1");
            } finally {
                session.rollback(true);
                session.clearCache();
            }
            assertNull(realMapper.selectByCode("TEST015-R-" + suffix));
            assertNull(realMapper.selectByCode("TEST015-A-" + suffix));
            assertNull(realMapper.selectByCode("TEST012-" + suffix));
            assertNull(realMapper.selectByCode("TEST012-new-import-" + suffix));
            assertNull(realMapper.selectByCode("TEST012-old-import-" + suffix));
            System.out.println("REAL_DB_EVIDENCE rollback=PASS no_test_materials_remaining");
        }
    }

}
