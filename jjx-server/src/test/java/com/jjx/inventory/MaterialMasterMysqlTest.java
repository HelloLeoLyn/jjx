package com.jjx.inventory;

import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.event.EventPublisher;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.mapper.*;
import com.jjx.inventory.master.*;
import com.jjx.inventory.classify.*;
import com.jjx.inventory.service.InventoryMaterialService;
import com.jjx.purchase.mapper.PurchaseSupplierMapper;
import com.jjx.system.mapper.SysDictItemMapper;
import com.jjx.system.mapper.SysDictMapper;
import com.jjx.system.service.ISysTagService;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 真MySQL的会话临时表隔离测试，不迁移目标库、不改持久业务行、不启动服务。 */
@EnabledIfEnvironmentVariable(named = "JJX_TEST_MYSQL", matches = "1")
class MaterialMasterMysqlTest {
    private SingleConnectionDataSource dataSource;
    private JdbcTemplate jdbc;
    private MaterialMasterService service;
    private MaterialMasterExcelService excel;
    private MaterialMasterCatalog catalog;
    private MaterialMasterConfigurationService configurationService;
    private ISysTagService tags;
    private ObjectMapper mapper;
    private int snapshotNumber;

    @BeforeEach void setup() throws Exception {
        DriverManagerDataSource source = new DriverManagerDataSource(
                System.getenv().getOrDefault("JJX_TEST_MYSQL_URL", "jdbc:mysql://127.0.0.1:3306/jjx_erp_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"),
                System.getenv().getOrDefault("DB_USER", "root"), System.getenv().getOrDefault("DB_PASS", "123456"));
        dataSource = new SingleConnectionDataSource(source.getConnection(), true);
        jdbc = new JdbcTemplate(dataSource);
        temporaryLike("inventory_material");
    }

    private void initialize() throws Exception {
        var existing = jdbc.queryForList("SHOW COLUMNS FROM inventory_material").stream().map(row -> row.get("Field").toString()).collect(java.util.stream.Collectors.toSet());
        Map<String, String> additions = Map.of("material_meta", "VARCHAR(30)", "material_form", "VARCHAR(20)", "attribute_json", "JSON",
                "is_customer_supplied", "TINYINT", "is_sample", "TINYINT",
                "class_source", "VARCHAR(10)", "class_confirmed", "TINYINT NOT NULL DEFAULT 0");
        for (var entry : additions.entrySet()) if (!existing.contains(entry.getKey())) jdbc.execute("ALTER TABLE inventory_material ADD COLUMN " + entry.getKey() + " " + entry.getValue());
        temporaryLike("inventory_material_category");
        if (jdbc.queryForList("SHOW COLUMNS FROM inventory_material_category").stream()
                .noneMatch(row -> "model_prefix".equals(row.get("Field"))))
            jdbc.execute("ALTER TABLE inventory_material_category ADD COLUMN model_prefix VARCHAR(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        temporaryLike("sys_dict_item");
        temporaryLike("sys_dict");
        for (String code : MaterialMasterCatalog.DICTIONARIES) jdbc.update("INSERT INTO sys_dict(dict_code,dict_name,dict_group,sort_order,is_active,deleted,tenant_id) VALUES (?,?,'inventory',0,1,0,1)",code,code);
        jdbc.execute("CREATE TEMPORARY TABLE inventory_transaction (material_id BIGINT) ENGINE=InnoDB");
        jdbc.execute("CREATE TEMPORARY TABLE inventory_attribute_template (template_id BIGINT AUTO_INCREMENT PRIMARY KEY,template_code VARCHAR(50),template_name VARCHAR(100),category_id BIGINT UNIQUE,attribute_schema JSON,status INT,create_by VARCHAR(64),update_by VARCHAR(64),create_time DATETIME,update_time DATETIME) ENGINE=InnoDB");
        jdbc.update("INSERT INTO inventory_material_category(category_id,category_code,category_name,parent_id,category_level,status,category_path) VALUES (900001,'RAW','原材料',0,1,'1','RAW'),(900002,'RAW_BASE','基材',900001,2,'1','RAW/RAW_BASE'),(900003,'RAW_BASE_PC','PC膜/片',900002,3,'1','RAW/RAW_BASE/RAW_BASE_PC'),(900004,'RAW_AUX','辅材',900001,2,'1','RAW/RAW_AUX'),(900005,'RAW_AUX_ELECTRONIC','电子元件',900004,3,'1','RAW/RAW_AUX/RAW_AUX_ELECTRONIC')");
        jdbc.update("UPDATE inventory_material_category SET model_prefix=CASE category_code WHEN 'RAW_BASE_PC' THEN 'PC' ELSE NULL END");
        String pc = """
                {"version":1,"meta_options":["PC"],"default_meta":"PC","default_form":"FILM","default_unit":"M","fields":[
                {"key":"anti_uv","label":"抗UV","type":"boolean","required":false}]}
                """;
        String electronic = """
                {"version":1,"meta_options":[],"default_form":"COMPONENT","default_unit":"PCS","fields":[
                {"key":"component_type","label":"元件类型","type":"enum","required":true,"options":[{"code":"LED","label":"LED"},{"code":"CONNECTOR","label":"连接器"}]}]}
                """;
        jdbc.update("INSERT INTO inventory_attribute_template(template_code,template_name,category_id,attribute_schema,status) VALUES ('TPL_PC','PC',900003,?,1),('TPL_ELECTRONIC','电子元件',900005,?,1)", pc, electronic);
        for (String[] value : List.of(new String[]{"material_meta","PC","PC"},new String[]{"form","FILM","膜"},new String[]{"form","COMPONENT","元件"},new String[]{"unit","M","米"},new String[]{"unit","PCS","件"},new String[]{"brand","3M","3M"},new String[]{"color","BLACK","黑色"})) {
            jdbc.update("INSERT INTO sys_dict_item(dict_code,item_key,item_value,label,sort_order,is_active,deleted,tenant_id) VALUES (?,?,?,?,1,1,0,1)",value[0],value[1],value[1],value[2]);
        }
        MybatisConfiguration config = new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true);
        for (Class<?> type : List.of(InventoryMaterialMapper.class, InventoryMaterialCategoryMapper.class, SysDictItemMapper.class, SysDictMapper.class, AttributeTemplateMapper.class, MaterialMasterMapper.class)) config.addMapper(type);
        MybatisSqlSessionFactoryBean bean = new MybatisSqlSessionFactoryBean(); bean.setDataSource(dataSource); bean.setConfiguration(config); bean.setGlobalConfig(new GlobalConfig().setBanner(false));
        var session = new SqlSessionTemplate(bean.getObject());
        mapper = new ObjectMapper().findAndRegisterModules();
        var materialMapper = session.getMapper(InventoryMaterialMapper.class);
        var categoryMapper = session.getMapper(InventoryMaterialCategoryMapper.class);
        var templateMapper = session.getMapper(AttributeTemplateMapper.class);
        var locks = session.getMapper(MaterialMasterMapper.class);
        catalog = new MaterialMasterCatalog(categoryMapper, templateMapper, session.getMapper(SysDictItemMapper.class), session.getMapper(SysDictMapper.class), mapper);
        InventoryMaterialService numbers = mock(InventoryMaterialService.class);
        when(numbers.generateCode(anyLong())).thenAnswer(i -> "T001" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        tags = mock(ISysTagService.class); when(tags.getBizTags(anyString(), anyLong())).thenReturn(List.of());
        var manager = new DataSourceTransactionManager(dataSource);
        service = proxy(new MaterialMasterService(catalog, locks, materialMapper, numbers, mock(PurchaseSupplierMapper.class), tags, mock(EventPublisher.class), mapper), manager);
        excel = proxy(new MaterialMasterExcelService(catalog, service, materialMapper, mapper, new MaterialClassifyService(mock(MaterialClassifyRuleMapper.class), materialMapper, locks, catalog, mapper)), manager);
        configurationService = proxy(new MaterialMasterConfigurationService(catalog, locks, categoryMapper, templateMapper, materialMapper, mapper), manager);
    }

    @SuppressWarnings("unchecked") private static <T> T proxy(T target, DataSourceTransactionManager manager) {
        var factory = new ProxyFactory(target); factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        return (T) factory.getProxy();
    }
    @AfterEach void close() { if (dataSource != null) dataSource.destroy(); }
    private void temporaryLike(String table) {
        String ddl = jdbc.queryForMap("SHOW CREATE TABLE " + table).get("Create Table").toString();
        ddl = ddl.replaceFirst("CREATE TABLE", "CREATE TEMPORARY TABLE")
                .replaceAll("(?m)^\\s*CONSTRAINT[^\\n]*\\n", "")
                .replaceAll(",\\s*\\) ENGINE", "\n) ENGINE");
        jdbc.execute(ddl);
    }
    private MaterialMasterDTO pc(String name) {
        var dto = new MaterialMasterDTO(); dto.setCategoryId(900003L); dto.setMaterialName(name); dto.setColor("BLACK");
        dto.setWidthMm(new java.math.BigDecimal("300")); dto.setLengthMm(new java.math.BigDecimal("305")); dto.setThicknessMm(null); dto.setSizeUnit("mm"); dto.setThicknessMm(new java.math.BigDecimal("0.175")); dto.setAttributeJson(Map.of()); return dto;
    }

    @Test void clearsDerivedColumnsOnMasterEdit() throws Exception {
        initialize();
        InventoryMaterial saved = service.save(pc("清空属性验证"), "Codex-test");
        var dto = pc("清空属性验证"); dto.setMaterialId(saved.getMaterialId()); dto.setAttributeJson(Map.of()); dto.setWidthMm(null); dto.setLengthMm(null); dto.setThicknessMm(null); dto.setSizeUnit("mm");
        InventoryMaterial edited = service.save(dto, "Codex-test");
        assertNotNull(edited.getTemplateId()); assertNull(edited.getThicknessMm()); assertNull(edited.getWidthMm());
        assertNull(jdbc.queryForObject("SELECT thickness_mm FROM inventory_material WHERE material_id=?", java.math.BigDecimal.class, edited.getMaterialId()));
    }

    @Test void savesDefaultsDerivesTypeAndPreservesSourceWithNoLegacyAttributeWrites() throws Exception {
        initialize();
        assertEquals(2, catalog.categories().stream().filter(MaterialMasterCatalog.Category::selectable).count());
        assertThrows(BusinessException.class, () -> catalog.configuration(900002L));
        InventoryMaterial material = service.save(pc("测试来源PC"), "Codex-test");
        assertEquals(900003L, material.getCategoryId()); assertEquals("PC", material.getMaterialMeta()); assertEquals("FILM", material.getMaterialForm());
        assertNull(com.jjx.inventory.master.MaterialSourceValues.from(material).originalSpecification()); assertEquals("尺寸:300*305*0.175mm", material.getSpecification());
        assertNotNull(material.getTemplateId());
        assertEquals(0, new java.math.BigDecimal("0.175").compareTo(material.getThicknessMm())); assertEquals("300*305*0.175mm", material.getSize());
        assertEquals("PC", jdbc.queryForObject("SELECT attr_meta FROM inventory_material WHERE material_id=?", String.class, material.getMaterialId()));
        assertEquals(0, new java.math.BigDecimal("0.1750").compareTo(jdbc.queryForObject("SELECT thickness_mm FROM inventory_material WHERE material_id=?", java.math.BigDecimal.class, material.getMaterialId())));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM inventory_material", Integer.class));
        assertFalse(service.detail(material.getMaterialId()).attributes().containsKey("thickness_mm"));
        var dto = pc("改名"); dto.setMaterialId(material.getMaterialId()); dto.setModel("M001"); service.save(dto,"Codex-test");
        dto.setModel(null); assertNull(service.save(dto,"Codex-test").getModel());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM inventory_material", Integer.class));
    }

    @Test void validationAndPostInsertFailureLeaveNoPartialMaterial() throws Exception {
        initialize();
        var dto = pc("非法属性"); dto.setThicknessMm(new java.math.BigDecimal("-0.175"));
        assertThrows(BusinessException.class, () -> service.save(dto,"Codex-test"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM inventory_material", Integer.class));
        doThrow(new BusinessException("标签写入失败")).when(tags).setBizTags(anyString(),anyLong(),anyList(),anyString());
        assertThrows(BusinessException.class, () -> service.save(pc("回滚测试"),"Codex-test"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM inventory_material", Integer.class));
    }

    @Test void usedTemplatesCannotBeReinterpretedAndUnitsWithTransactionsCannotChange() throws Exception {
        initialize();
        var material = service.save(pc("使用模板"),"Codex-test");
        var schema = catalog.configuration(900003L).schema();
        assertDoesNotThrow(() -> configurationService.saveTemplate(new MaterialMasterConfigurationService.TemplateSave("TPL_PC","改显示名",900003L,schema,1),"Codex-test"));
        var changed = new MaterialAttributeSchema(2,schema.metaOptions(),schema.metaRequired(),schema.defaultMeta(),schema.defaultForm(),schema.defaultUnit(),List.of(),null);
        assertThrows(BusinessException.class, () -> configurationService.saveTemplate(new MaterialMasterConfigurationService.TemplateSave("TPL_PC","PC",900003L,changed,1),"Codex-test"));
        jdbc.update("INSERT INTO inventory_transaction(material_id) VALUES (?)",material.getMaterialId());
        var dto = pc("使用模板"); dto.setMaterialId(material.getMaterialId()); dto.setUnit("PCS");
        assertThrows(BusinessException.class, () -> service.save(dto,"Codex-test"));
        assertEquals("M", jdbc.queryForObject("SELECT unit FROM inventory_material WHERE material_id=?",String.class,material.getMaterialId()));
    }

    private MockMultipartFile file(boolean invalidSecond) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excel.template(900003L)))) {
            var sheet = workbook.getSheetAt(0);
            for (int i=0;i<2;i++) {
                var row=sheet.createRow(i+2); row.createCell(0).setCellValue("RAW_BASE_PC"); row.createCell(1).setCellValue("批量PC"+i);
                for (var cell : sheet.getRow(1)) {
                    String header = cell.getStringCellValue();
                    if ("颜色".equals(header)) row.createCell(cell.getColumnIndex()).setCellValue("BLACK");
                    if ("宽(mm)".equals(header)) row.createCell(cell.getColumnIndex()).setCellValue("300");
                    if ("长(mm)".equals(header)) row.createCell(cell.getColumnIndex()).setCellValue("305");
                    if ("厚(mm)".equals(header)) row.createCell(cell.getColumnIndex()).setCellValue(invalidSecond && i==1 ? "错误厚度" : "0.175");
                    if ("尺寸单位".equals(header)) row.createCell(cell.getColumnIndex()).setCellValue("mm");
                }
            }
            var output = new ByteArrayOutputStream(); workbook.write(output);
            return new MockMultipartFile("file","pc.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",output.toByteArray());
        }
    }

    @Test void importPreflightAndTransactionalRollbackCoverTheWholeWorkbook() throws Exception {
        initialize();
        var bad = excel.importExcel(file(true),900003L,true,"Codex-test",false);
        assertEquals(0,bad.successCount()); assertTrue(bad.errors().get(0).contains("第4行"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM inventory_material",Integer.class));
        assertEquals(2,excel.importExcel(file(false),900003L,true,"Codex-test",false).successCount());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM inventory_material",Integer.class));
        int[] calls={0}; doAnswer(i->{if(++calls[0]==2)throw new BusinessException("第二行写入失败");return null;}).when(tags).setBizTags(anyString(),anyLong(),anyList(),anyString());
        assertThrows(BusinessException.class,()->excel.importExcel(file(false),900003L,false,"Codex-test",false));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM inventory_material",Integer.class));
        reset(tags); when(tags.getBizTags(anyString(),anyLong())).thenReturn(List.of());
        assertEquals(2,excel.importExcel(file(false),900003L,false,"Codex-test",false).successCount());
        try (XSSFWorkbook exported=new XSSFWorkbook(new ByteArrayInputStream(excel.export(null)))) { assertEquals(2,exported.getSheetAt(0).getLastRowNum()); assertEquals("原材料",exported.getSheetAt(0).getRow(1).getCell(3).getStringCellValue()); }
    }

    @Test void migrationByIdRequiresEditPermissionAndPreservesCodeAndBusinessParameters() throws Exception {
        initialize();
        var old = service.save(pc("迁移前"),"Codex-test");
        jdbc.update("UPDATE inventory_material SET attribute_json=NULL,class_confirmed=0,safe_stock=7,standard_price=1.25 WHERE material_id=?",old.getMaterialId());
        MockMultipartFile migration;
        try (XSSFWorkbook workbook=new XSSFWorkbook(new ByteArrayInputStream(file(false).getBytes()))) {
            for (var cell : workbook.getSheetAt(0).getRow(1))
                if ("物料ID（更新时填写）".equals(cell.getStringCellValue()))
                    workbook.getSheetAt(0).getRow(2).createCell(cell.getColumnIndex()).setCellValue(old.getMaterialId().toString());
            var output=new ByteArrayOutputStream();workbook.write(output);
            migration=new MockMultipartFile("file","migration.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",output.toByteArray());
        }
        var rejected=excel.importExcel(migration,900003L,true,"Codex-test",false);
        assertEquals(0,rejected.successCount());assertTrue(rejected.errors().stream().anyMatch(e->e.contains("编辑权限")));
        assertEquals(2,excel.importExcel(migration,900003L,false,"Codex-test",true).successCount());
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM inventory_material",Integer.class));
        var row=jdbc.queryForMap("SELECT material_code,material_name,safe_stock,standard_price FROM inventory_material WHERE material_id=?",old.getMaterialId());
        assertEquals(old.getMaterialCode(),row.get("material_code"));assertEquals("批量PC0",row.get("material_name"));
        assertEquals(0,new java.math.BigDecimal("7").compareTo((java.math.BigDecimal)row.get("safe_stock")));
        assertEquals(0,new java.math.BigDecimal("1.25").compareTo((java.math.BigDecimal)row.get("standard_price")));
    }

    @Test void numericContractRoundTripsAndOldTemplateIsRejected() throws Exception {
        initialize();
        var columns = jdbc.queryForList("SHOW COLUMNS FROM inventory_material").stream().map(r -> r.get("Field").toString()).toList();
        for (String retired : List.of("size", "thickness", "width_size", "attr_thickness_num")) assertFalse(columns.contains(retired));
        var dto = mapper.readValue("{\"materialName\":\"数值尺寸\",\"categoryId\":900003,\"color\":\"BLACK\",\"widthMm\":30,\"lengthMm\":90,\"thicknessMm\":0.175,\"sizeUnit\":\"cm\",\"size\":\"伪造\"}", MaterialMasterDTO.class);
        var saved = service.save(dto, "Codex-test");
        // 宽/长/厚均以 mm 入库；旧 sizeUnit 不参与结构化尺寸渲染。
        assertEquals("30*90*0.175mm", mapper.readTree(mapper.writeValueAsString(saved)).get("size").asText());
        assertEquals("30*90*0.175mm", service.detail(saved.getMaterialId()).material().getSize());
        assertEquals(0, new java.math.BigDecimal("30").compareTo(saved.getWidthMm()));
        assertEquals(0, new java.math.BigDecimal("90").compareTo(saved.getLengthMm()));
        assertEquals(0, new java.math.BigDecimal("0.175").compareTo(saved.getThicknessMm()));
        assertTrue(jdbc.queryForObject("SELECT search_blob FROM inventory_material WHERE material_id=?", String.class, saved.getMaterialId()).contains(saved.getSize()));
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excel.export(null)))) {
            var sheet = workbook.getSheetAt(0);
            assertEquals(sheet.getRow(0).getLastCellNum(), sheet.getRow(1).getLastCellNum());
            // 四级分类导出列已加入，尺寸列随之位于第 10 列。
            assertEquals("四级分类", sheet.getRow(0).getCell(6).getStringCellValue());
            assertEquals("尺寸(mm)", sheet.getRow(0).getCell(9).getStringCellValue());
            assertEquals(saved.getSize(), sheet.getRow(1).getCell(9).getStringCellValue());
        }
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet(); var header = sheet.createRow(0);
            header.createCell(0).setCellValue("物料名称"); header.createCell(1).setCellValue("尺寸(mm)");
            var output = new ByteArrayOutputStream(); workbook.write(output);
            var old = new MockMultipartFile("file", "old.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray());
            assertTrue(assertThrows(BusinessException.class, () -> excel.importExcel(old, null, true, "Codex-test", false)).getMessage().contains("缺【宽(mm)】列"));
        }
    }

    @Test void requiredEditablePrefixesAreUniqueAndModelRuleChangesTakeEffect() throws Exception {
        initialize();
        for (String invalid : Arrays.asList(null, "", " ", "P", "ABCDEFGHI", "pc", "P-C")) {
            var dto = new MaterialMasterConfigurationService.CategorySave(900003L, "RAW_BASE_PC", "PC膜/片", 900002L, 0, 1, invalid);
            assertThrows(BusinessException.class, () -> configurationService.saveCategory(dto, "Codex-test"));
        }
        var duplicate = new MaterialMasterConfigurationService.CategorySave(null, "RAW_BASE_TEST", "重复前缀", 900002L, 0, 1, "PC");
        assertThrows(BusinessException.class, () -> configurationService.saveCategory(duplicate, "Codex-test"));
        assertEquals(5, jdbc.queryForObject("SELECT COUNT(*) FROM inventory_material_category", Integer.class));
        var edit = new MaterialMasterConfigurationService.CategorySave(900003L, "RAW_BASE_PC", "PC膜/片", 900002L, 0, 1, "PCX");
        configurationService.saveCategory(edit, "Codex-test");
        assertEquals("PCX", catalog.configuration(900003L).category().modelPrefix());
        var initial = catalog.configuration(900003L).schema();
        var next = mapper.readTree(mapper.writeValueAsString(initial));
        ((com.fasterxml.jackson.databind.node.ObjectNode) next).set("model_pattern", mapper.readTree(
                "{\"fields\":[\"categoryprefix\",\"grammage\"],\"separator\":\"-\",\"empty\":\"skip\"}"));
        configurationService.saveTemplate(new MaterialMasterConfigurationService.TemplateSave(
                "TPL_PC", "PC", 900003L, MaterialAttributeSchema.parse(next.toString(), mapper), 1), "Codex-test");
        var draft = pc("规则改克重"); draft.setThicknessMm(null); draft.setGrammage(new java.math.BigDecimal("80"));
        assertEquals("PCX-80", service.renderModel(900003L, draft, null));
        assertEquals("PCX-80", service.save(draft, "Codex-test").getModel());
        jdbc.update("UPDATE inventory_material_category SET model_prefix=NULL WHERE category_id=900003");
        assertTrue(assertThrows(BusinessException.class, () -> service.save(draft, "Codex-test")).getMessage().contains("配置型号前缀"));
        assertThrows(BusinessException.class, () -> service.renderModel(900003L, draft, null));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM inventory_material", Integer.class));
    }

    @Test void parentsAllowBlankPrefixesButLeavesIncludingDisabledChildrenStillRequireThem() throws Exception {
        initialize();
        var child = configurationService.saveCategory(new MaterialMasterConfigurationService.CategorySave(
                null, "RAW_AUX_LED", "LED", 900005L, 0, 1, "LED"), "Codex-test");
        for (String blank : Arrays.asList(null, "", " ")) {
            var parent = new MaterialMasterConfigurationService.CategorySave(
                    900005L, "RAW_AUX_ELECTRONIC", "电子元件", 900004L, 0, 1, blank);
            assertDoesNotThrow(() -> configurationService.saveCategory(parent, "Codex-test"));
            assertEquals(blank, jdbc.queryForObject("SELECT model_prefix FROM inventory_material_category WHERE category_id=900005", String.class));
            var leaf = new MaterialMasterConfigurationService.CategorySave(
                    child.getCategoryId(), "RAW_AUX_LED", "LED", 900005L, 0, 1, blank);
            assertTrue(assertThrows(BusinessException.class, () -> configurationService.saveCategory(leaf, "Codex-test"))
                    .getMessage().contains("型号前缀不能为空"));
            var newLeaf = new MaterialMasterConfigurationService.CategorySave(
                    null, "RAW_BASE_EMPTY", "空前缀叶子", 900002L, 0, 1, blank);
            assertThrows(BusinessException.class, () -> configurationService.saveCategory(newLeaf, "Codex-test"));
        }
        var duplicateParent = new MaterialMasterConfigurationService.CategorySave(
                900005L, "RAW_AUX_ELECTRONIC", "电子元件", 900004L, 0, 1, "PC");
        assertTrue(assertThrows(BusinessException.class, () -> configurationService.saveCategory(duplicateParent, "Codex-test"))
                .getMessage().contains("已被其他品类使用"));
        var uniqueParent = new MaterialMasterConfigurationService.CategorySave(
                900005L, "RAW_AUX_ELECTRONIC", "电子元件", 900004L, 0, 1, "ELEC");
        assertDoesNotThrow(() -> configurationService.saveCategory(uniqueParent, "Codex-test"));
        assertDoesNotThrow(() -> configurationService.saveCategory(uniqueParent, "Codex-test"));
        jdbc.update("UPDATE inventory_material_category SET status='0' WHERE category_id=?", child.getCategoryId());
        for (String invalid : Arrays.asList(null, "", " ", "P", "pc", "P-C")) {
            var nowLeaf = new MaterialMasterConfigurationService.CategorySave(
                    900005L, "RAW_AUX_ELECTRONIC", "电子元件", 900004L, 0, 1, invalid);
            assertThrows(BusinessException.class, () -> configurationService.saveCategory(nowLeaf, "Codex-test"));
        }
        assertEquals("ELEC", jdbc.queryForObject("SELECT model_prefix FROM inventory_material_category WHERE category_id=900005", String.class));
        assertEquals("LED", jdbc.queryForObject("SELECT model_prefix FROM inventory_material_category WHERE category_id=?", String.class, child.getCategoryId()));
        assertEquals(6, jdbc.queryForObject("SELECT COUNT(*) FROM inventory_material_category", Integer.class));
    }

    @Test void duplicateModelsAreAllowedWhileIdenticalRowsAreRejectedOnImport() throws Exception {
        initialize();
        var first = pc("同型号一"); first.setModel("GLOBAL125"); first.setSupplierName("供应商甲");
        service.save(first, "Codex-test");
        // modelFile 第二行用另一个品类，确保文件查重跨品类
        var category = configurationService.saveCategory(new MaterialMasterConfigurationService.CategorySave(
                null, "RAW_BASE_TEST", "另一品类", 900002L, 0, 1, "PC2"), "Codex-test");
        configurationService.saveTemplate(new MaterialMasterConfigurationService.TemplateSave(
                "TPL_PC2", "另一品类", category.getCategoryId(), catalog.configuration(900003L).schema(), 1), "Codex-test");
        // 同型号不同供应商可并存
        var otherSupplier = pc("同型号二"); otherSupplier.setModel("global125"); otherSupplier.setSupplierName("供应商乙");
        assertDoesNotThrow(() -> service.save(otherSupplier, "Codex-test"));
        // 同供应商同型号不同规格也可并存
        var otherSize = pc("同型号三"); otherSize.setModel("global125"); otherSize.setSupplierName("供应商甲");
        otherSize.setWidthMm(new java.math.BigDecimal("400"));
        assertDoesNotThrow(() -> service.save(otherSize, "Codex-test"));
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM inventory_material", Integer.class));
        // 导入：同型号不同供应商 → 放行
        var result = excel.importExcel(modelFile("OK125", "OK125", "供应商丙", "供应商丁"), null, false, "Codex-test", false);
        assertEquals(2, result.successCount(), result.errors().toString());
        assertEquals(5, jdbc.queryForObject("SELECT COUNT(*) FROM inventory_material", Integer.class));
        // 导入：各关键字段完全相同（含同供应商）的两行 → 仍按重复物料拦截，整表不写
        result = excel.importExcel(duplicateFile("DUP125", "供应商丙"), null, false, "Codex-test", false);
        assertEquals(0, result.successCount());
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("第3行") && error.contains("与文件其他行或库内物料重复")), result.errors().toString());
        assertEquals(5, jdbc.queryForObject("SELECT COUNT(*) FROM inventory_material", Integer.class));
    }

    /** 两行完全相同（名称/品类/型号/规格/颜色/供应商一致），用于验证「数据一样才拦」。 */
    private MockMultipartFile duplicateFile(String model, String supplier) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet(); var header = sheet.createRow(0);
            String[] columns = {"物料名称", "品类", "型号", "宽(mm)", "长(mm)", "厚(mm)", "尺寸单位", "颜色", "供应商"};
            for (int i = 0; i < columns.length; i++) header.createCell(i).setCellValue(columns[i]);
            for (int i = 0; i < 2; i++) {
                var row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue("重复行");
                row.createCell(1).setCellValue("RAW_BASE_PC");
                row.createCell(2).setCellValue(model);
                row.createCell(3).setCellValue("300"); row.createCell(4).setCellValue("305"); row.createCell(5).setCellValue("0.175");
                row.createCell(7).setCellValue("BLACK");
                row.createCell(8).setCellValue(supplier);
            }
            workbook.write(output);
            return new MockMultipartFile("file", "duplicates.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray());
        }
    }

    private MockMultipartFile modelFile(String first, String second, String firstSupplier, String secondSupplier) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet(); var header = sheet.createRow(0);
            String[] columns = {"物料名称", "品类", "型号", "宽(mm)", "长(mm)", "厚(mm)", "尺寸单位", "颜色", "供应商"};
            for (int i = 0; i < columns.length; i++) header.createCell(i).setCellValue(columns[i]);
            for (int i = 0; i < 2; i++) {
                var row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue("型号导入" + i);
                // 两行属于不同品类，证明文件查重跨品类。
                row.createCell(1).setCellValue(i == 0 ? "RAW_BASE_PC" : "RAW_BASE_TEST");
                row.createCell(2).setCellValue(i == 0 ? first : second);
                row.createCell(3).setCellValue("300"); row.createCell(4).setCellValue("305"); row.createCell(5).setCellValue("0.175");
                row.createCell(7).setCellValue("BLACK");
                row.createCell(8).setCellValue(i == 0 ? firstSupplier : secondSupplier);
            }
            workbook.write(output);
            return new MockMultipartFile("file", "models.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray());
        }
    }

    @Test void exactMigrationSeedsExecuteAndAreIdempotentInSessionTemporaryTables() throws Exception {
        temporaryLike("inventory_material_category"); temporaryLike("sys_dict"); temporaryLike("sys_dict_item");
        String ddl = migration("303_material_master_schema.sql")
                .replace("CREATE TABLE IF NOT EXISTS inventory_attribute_template", "CREATE TEMPORARY TABLE IF NOT EXISTS inventory_attribute_template")
                .replaceAll(",\\s*CONSTRAINT [^\\n]+", ""); // MySQL会话临时表不支持外键；正式FK验收仍须实际迁移。
        executeStatements(ddl);
        // 304 使用历史分类 material_type；当前由分类树根节点推导类型。
        // 只移除分类 INSERT 的已删列及对应值，保留种子内容、条件与幂等断言。
        String seeds=migration("304_material_master_catalog.sql").lines().map(line ->
                line.startsWith("INSERT INTO inventory_material_category ")
                        ? line.replace(",material_type,", ",").replaceAll(",'[RSF]',(?=[123],)", ",")
                        : line).collect(java.util.stream.Collectors.joining("\n"));
        assertFalse(seeds.contains("material_type"));
        executeStatements(seeds); executeStatements(seeds);
        assertEquals(16,jdbc.queryForObject("SELECT COUNT(*) FROM inventory_attribute_template",Integer.class));
        assertEquals(19,jdbc.queryForObject("SELECT COUNT(*) FROM inventory_material_category",Integer.class));
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM inventory_material_category WHERE parent_id=0",Integer.class));
        assertEquals("RAW/RAW_BASE/RAW_BASE_PC",jdbc.queryForObject("SELECT category_path FROM inventory_material_category WHERE category_code='RAW_BASE_PC'",String.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM inventory_material",Integer.class));
    }

    private static String migration(String name) throws Exception {
        java.nio.file.Path path=java.nio.file.Path.of("../jjx-docs/sql/migrations",name);
        if(!java.nio.file.Files.exists(path))path=java.nio.file.Path.of("../jjx-docs/sql/migrations/applied",name);
        return java.nio.file.Files.readString(path);
    }
    private void executeStatements(String source) {
        String sql=source.replaceAll("(?m)^\\s*--[^\\r\\n]*", "");
        StringBuilder statement=new StringBuilder(); boolean quoted=false;
        for(int i=0;i<sql.length();i++){
            char c=sql.charAt(i);
            if(c=='\\' && quoted && i+1<sql.length()){statement.append(c).append(sql.charAt(++i));continue;}
            if(c=='\''){
                if(quoted && i+1<sql.length() && sql.charAt(i+1)=='\''){statement.append(c).append(sql.charAt(++i));continue;}
                quoted=!quoted;
            }
            if(c==';' && !quoted){if(!statement.toString().isBlank())executeMigrationStatement(statement.toString());statement.setLength(0);}
            else statement.append(c);
        }
        if(!statement.toString().isBlank())executeMigrationStatement(statement.toString());
    }
    private void executeMigrationStatement(String statement) {
        if(statement.stripLeading().startsWith("INSERT")){
            // MySQL禁止在同一语句多次打开同一会话临时表；读别名转到该语句前的临时快照。
            // 仅适配测试隔离载体，保留种子SQL的条件、值和写入目标；正式表不需要此适配。
            var matcher=java.util.regex.Pattern.compile("(?i)(FROM|JOIN)\\s+(sys_dict_item|sys_dict|inventory_material_category|inventory_attribute_template)\\b").matcher(statement);
            StringBuffer rewritten=new StringBuffer();
            while(matcher.find()){
                String table=matcher.group(2), snapshot="master_seed_source_"+(++snapshotNumber);
                String ddl=jdbc.queryForMap("SHOW CREATE TABLE "+table).get("Create Table").toString().replaceFirst("`"+table+"`","`"+snapshot+"`");
                jdbc.execute(ddl);jdbc.update("INSERT INTO "+snapshot+" SELECT * FROM "+table);
                matcher.appendReplacement(rewritten,matcher.group(1)+" "+snapshot);
            }
            matcher.appendTail(rewritten);statement=rewritten.toString();
        }
        jdbc.execute(statement);
    }
}
