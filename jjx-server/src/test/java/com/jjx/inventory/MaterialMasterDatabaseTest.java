package com.jjx.inventory;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.event.EventPublisher;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.mapper.InventoryMaterialCategoryMapper;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.inventory.master.*;
import com.jjx.inventory.service.InventoryMaterialService;
import com.jjx.purchase.mapper.PurchaseSupplierMapper;
import com.jjx.system.mapper.*;
import com.jjx.system.service.ISysTagService;
import com.jjx.system.service.impl.SysTagServiceImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 303/304正式应用后的真实库事务验证：读取实际分类/字典/模板，写入只在外层事务内可见，每项回滚。
 * 不创建替代表、不启服务、不改库存；编号生成与事件投递替换，物料及标签Mapper为真实实现。
 */
@EnabledIfEnvironmentVariable(named = "JJX_TEST_MASTER_DATABASE", matches = "1")
class MaterialMasterDatabaseTest {
    private static JdbcTemplate jdbc;
    private static DataSourceTransactionManager manager;
    private static MaterialMasterCatalog catalog;
    private static MaterialMasterService service;
    private static ISysTagService tags;
    private static MaterialMasterExcelService excel;
    private TransactionStatus transaction;

    @BeforeAll static void setup() throws Exception {
        var source = new DriverManagerDataSource(
                System.getenv().getOrDefault("JJX_TEST_MYSQL_URL", "jdbc:mysql://127.0.0.1:3306/jjx_erp_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"),
                System.getenv().getOrDefault("DB_USER", "root"), System.getenv().getOrDefault("DB_PASS", "123456"));
        manager = new DataSourceTransactionManager(source);
        jdbc = new JdbcTemplate(source);
        assertEquals("304", jdbc.queryForObject("SELECT config_value FROM sys_config WHERE config_key='ops.schema.version'", String.class));
        MybatisConfiguration config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        for (Class<?> type : List.of(InventoryMaterialMapper.class, InventoryMaterialCategoryMapper.class,
                AttributeTemplateMapper.class, MaterialMasterMapper.class, SysDictMapper.class, SysDictItemMapper.class,
                SysTagMapper.class, SysTagRelMapper.class)) config.addMapper(type);
        var bean = new MybatisSqlSessionFactoryBean();
        bean.setDataSource(source); bean.setConfiguration(config); bean.setGlobalConfig(new GlobalConfig().setBanner(false));
        var session = new SqlSessionTemplate(bean.getObject());
        var materialMapper = session.getMapper(InventoryMaterialMapper.class);
        ObjectMapper objectMapper = new ObjectMapper();
        catalog = new MaterialMasterCatalog(session.getMapper(InventoryMaterialCategoryMapper.class),
                session.getMapper(AttributeTemplateMapper.class), session.getMapper(SysDictItemMapper.class),
                session.getMapper(SysDictMapper.class), objectMapper);
        var numbers = mock(InventoryMaterialService.class);
        when(numbers.existsByCode(anyString())).thenAnswer(invocation -> materialMapper.selectByCode(invocation.getArgument(0)) != null);
        when(numbers.generateCode(anyLong())).thenAnswer(invocation -> "T002DB" + UUID.randomUUID().toString().replace("-", ""));
        tags = proxy(new SysTagServiceImpl(session.getMapper(SysTagMapper.class), session.getMapper(SysTagRelMapper.class)));
        service = proxy(new MaterialMasterService(catalog, session.getMapper(MaterialMasterMapper.class), materialMapper,
                numbers, mock(PurchaseSupplierMapper.class), tags, mock(EventPublisher.class), objectMapper));
        excel = proxy(new MaterialMasterExcelService(catalog, service, materialMapper, objectMapper));
    }

    @SuppressWarnings("unchecked") private static <T> T proxy(T target) {
        ProxyFactory factory = new ProxyFactory(target); factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        return (T) factory.getProxy();
    }

    @BeforeEach void begin() { transaction = manager.getTransaction(new DefaultTransactionDefinition()); }
    @AfterEach void rollback() { if (transaction != null && !transaction.isCompleted()) manager.rollback(transaction); }
    @AfterAll static void noTestRowsRemain() {
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM inventory_material WHERE material_code LIKE 'T002DB%'", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM inventory_attribute_template WHERE template_code LIKE 'T002DB%'", Integer.class));
    }

    @Test void actualCatalogHasThreeRootsNineteenNodesAndSixteenUsableTemplates() {
        var categories = catalog.categories();
        assertEquals(19, categories.size());
        assertEquals(3, categories.stream().filter(c -> c.categoryLevel() == 1).count());
        assertEquals(16, categories.stream().filter(MaterialMasterCatalog.Category::selectable).count());
        for (var category : categories) if (category.selectable()) {
            var configuration = catalog.configuration(category.categoryId());
            configuration.schema().validateDefinition();
            for (String meta : configuration.schema().metaOptions()) assertFalse(catalog.dictionaryLabel("material_meta", meta, true).isBlank());
            assertFalse(catalog.dictionaryLabel("unit", configuration.schema().defaultUnit(), true).isBlank());
        }
    }

    @Test void actualForeignKeyAndUniqueCategoryRejectInvalidTemplateWrites() {
        Long categoryId = category("RAW_BASE_PC");
        DataAccessException duplicate = assertThrows(DataAccessException.class, () -> jdbc.update(
                "INSERT INTO inventory_attribute_template(template_code,template_name,category_id,attribute_schema) VALUES (?,'test',?,'{}')", "T002DB_DUP", categoryId));
        assertTrue(duplicate.getMostSpecificCause().getMessage().contains("Duplicate"));
        DataAccessException foreignKey = assertThrows(DataAccessException.class, () -> jdbc.update(
                "INSERT INTO inventory_attribute_template(template_code,template_name,category_id,attribute_schema) VALUES (?,'test',-1,'{}')", "T002DB_FK"));
        assertTrue(foreignKey.getMostSpecificCause().getMessage().contains("foreign key"));
    }

    @Test void materialAndRealTagRelationsSaveTogetherAndReadonlySqlChecksActualValidRow() throws Exception {
        String code = "T002DB" + UUID.randomUUID().toString().replace("-", "");
        var dto = new MaterialMasterDTO();
        dto.setCategoryId(category("RAW_BASE_PC")); dto.setMaterialCode(code);
        dto.setOriginalName("阶段6事务验证PC"); dto.setOriginalSpecification("来源300*305"); dto.setSize("300×305");
        dto.setAttributeJson(Map.of("thickness_mm", new BigDecimal("0.175"), "surface_treatment", List.of("MEDIUM_SAND", "HARD_COATED"), "anti_uv", false));
        var availableTags = tags.listTags("material_attribute", null, 1);
        List<Long> selectedTags = availableTags.isEmpty() ? List.of() : List.of(availableTags.get(0).getTagId());
        dto.setTagIds(selectedTags);
        InventoryMaterial saved = service.save(dto, "Codex-db-test");
        assertEquals(category("RAW_BASE_PC"), saved.getCategoryId()); assertEquals("PC", saved.getMaterialMeta()); assertEquals("FILM", saved.getMaterialForm());
        assertEquals("来源300*305", saved.getOriginalSpecification()); assertFalse(service.detail(saved.getMaterialId()).attributes().get("anti_uv").equals(true));
        assertEquals(selectedTags.size(), tags.getBizTags("inventory_material", saved.getMaterialId()).size());
        dto.setMaterialId(saved.getMaterialId()); dto.setModel("DB6842"); service.save(dto, "Codex-db-test");
        dto.setModel(null); assertNull(service.save(dto, "Codex-db-test").getModel());
        assertEquals(code, jdbc.queryForObject("SELECT material_code FROM inventory_material WHERE material_id=?", String.class, saved.getMaterialId()));
        String checks = Files.readString(Path.of("../jjx-docs/sql/material_master_checks.sql")).replaceAll("(?m)^--[^\\r\\n]*", "");
        for (String statement : checks.split(";")) {
            if (statement.isBlank()) continue;
            var rows = jdbc.queryForList(statement);
            if (!rows.isEmpty() && rows.get(0).keySet().stream().anyMatch(k -> k.startsWith("new_model_"))) {
                for (Object value : rows.get(0).values()) assertEquals(0, ((Number) value).intValue());
            }
            if (!rows.isEmpty() && rows.get(0).containsKey("material_id")) fail("只读校验发现问题：" + rows);
        }
    }

    private static Long category(String code) {
        return jdbc.queryForObject("SELECT category_id FROM inventory_material_category WHERE category_code=?", Long.class, code);
    }

    @Test void realDictionaryLabelsRoundTripThroughGeneratedExcelAndLeaveNoPersistentRows() throws Exception {
        Long categoryId = category("RAW_BASE_PC");
        MockMultipartFile file;
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excel.template(categoryId)))) {
            var row = workbook.getSheetAt(0).createRow(2);
            Map<Integer,String> values = Map.ofEntries(Map.entry(0,"正式库Excel事务验证"),Map.entry(1,"来源300*305"),
                    Map.entry(2,"PC"),Map.entry(3,"膜"),Map.entry(4,"300×305"),Map.entry(5,"透明"),
                    Map.entry(6,"DB6842"),Map.entry(7,"地博"),Map.entry(8,"米"),Map.entry(10,"否"),Map.entry(11,"否"),
                    Map.entry(15,"0.175"),Map.entry(16,"中砂、加硬"),Map.entry(20,"否"));
            values.forEach((column,value) -> row.createCell(column).setCellValue(value));
            var output = new ByteArrayOutputStream(); workbook.write(output);
            file = new MockMultipartFile("file","pc.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",output.toByteArray());
        }
        assertEquals(1,excel.importExcel(file,categoryId,true,"Codex-db-test",false).successCount());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM inventory_material WHERE material_code LIKE 'T002DB%'",Integer.class));
        assertEquals(1,excel.importExcel(file,categoryId,false,"Codex-db-test",false).successCount());
        var row=jdbc.queryForMap("SELECT color,brand,unit,attribute_json FROM inventory_material WHERE material_code LIKE 'T002DB%'");
        assertEquals("TRANSPARENT",row.get("color")); assertEquals("DIBO",row.get("brand")); assertEquals("M",row.get("unit"));
        try(XSSFWorkbook exported=new XSSFWorkbook(new ByteArrayInputStream(excel.export(categoryId)))) {
            assertEquals(1,exported.getSheetAt(0).getLastRowNum());
            assertEquals("正式库Excel事务验证",exported.getSheetAt(0).getRow(1).getCell(3).getStringCellValue());
            assertEquals("来源300*305",exported.getSheetAt(0).getRow(1).getCell(4).getStringCellValue());
        }
    }
}
