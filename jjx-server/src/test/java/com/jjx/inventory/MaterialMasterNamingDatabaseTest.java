package com.jjx.inventory;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.event.EventPublisher;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.inventory.master.*;
import com.jjx.inventory.mapper.InventoryMaterialCategoryMapper;
import com.jjx.system.mapper.SysDictMapper;
import com.jjx.system.mapper.SysDictItemMapper;
import com.jjx.system.service.ISysTagService;
import com.jjx.purchase.mapper.PurchaseSupplierMapper;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.session.SaSession;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.MediaType;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import com.jjx.inventory.service.impl.InventoryMaterialServiceImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 真实业务库、真实Mapper及master事务代理；每次测试整体回滚，回滚后再次查询原行。 */
@EnabledIfEnvironmentVariable(named = "JJX_TEST_MASTER_DATABASE", matches = "1")
class MaterialMasterNamingDatabaseTest {
    private static JdbcTemplate jdbc;
    private static DataSourceTransactionManager manager;
    private static MaterialMasterService service;
    private static InventoryMaterialServiceImpl legacy;
    private static MaterialMasterCatalog catalog;
    private static final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @BeforeAll static void setup() throws Exception {
        var source = new DriverManagerDataSource(System.getenv().getOrDefault("JJX_TEST_MYSQL_URL",
                "jdbc:mysql://127.0.0.1:3306/jjx_erp_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"),
                System.getenv().getOrDefault("DB_USER", "root"), System.getenv().getOrDefault("DB_PASS", "123456"));
        jdbc = new JdbcTemplate(source); manager = new DataSourceTransactionManager(source);
        var config = new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true);
        for (Class<?> type : List.of(InventoryMaterialMapper.class, MaterialMasterMapper.class,
                InventoryMaterialCategoryMapper.class, AttributeTemplateMapper.class, SysDictMapper.class, SysDictItemMapper.class))
            config.addMapper(type);
        var bean = new MybatisSqlSessionFactoryBean(); bean.setDataSource(source);
        bean.setConfiguration(config); bean.setGlobalConfig(new GlobalConfig().setBanner(false));
        var session = new SqlSessionTemplate(bean.getObject());
        var materials = session.getMapper(InventoryMaterialMapper.class);
        catalog = new MaterialMasterCatalog(session.getMapper(InventoryMaterialCategoryMapper.class),
                session.getMapper(AttributeTemplateMapper.class), session.getMapper(SysDictItemMapper.class),
                session.getMapper(SysDictMapper.class), json);
        var target = new MaterialMasterService(catalog, session.getMapper(MaterialMasterMapper.class), materials,
                null, mock(PurchaseSupplierMapper.class), mock(ISysTagService.class), mock(EventPublisher.class), json);
        var proxy = new ProxyFactory(target); proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        service = (MaterialMasterService) proxy.getProxy();
        legacy = new InventoryMaterialServiceImpl(materials, null, null, null, null, null, null, null, null, null, null);
    }

    @Test void classifiedNamingPreservesEveryOtherColumnAndOldSaveStillRejects() { check(false); }
    @Test void unclassifiedNamingAlsoPreservesEveryOtherColumn() { check(true); }

    @Test void historicalNonLeafCategoryWithoutTemplateCanStillLoadAndRename() {
        Long id = materialId();
        Map<String, Object> original = row(id);
        Long root = jdbc.queryForObject("SELECT category_id FROM inventory_material_category WHERE category_code='RAW'", Long.class);
        new TransactionTemplate(manager).executeWithoutResult(tx -> {
            tx.setRollbackOnly();
            jdbc.update("UPDATE inventory_material SET category_id=?,specification='历史无模板规格' WHERE material_id=?", root, id);
            var before = row(id);
            assertNull(service.detail(id).configuration());
            var saved = service.updateNaming(id, new MaterialMasterService.NamingPatch("历史分类重算", "伪造", null), "codex-test");
            assertEquals("历史无模板规格", saved.material().getSpecification());
            assertFalse(saved.specificationGeneration().generated());
            assertEquals(protectedColumns(before), protectedColumns(row(id)));
        });
        assertEquals(original, row(id));
        System.out.println("SPEC_HISTORICAL_CATEGORY rollback verified id=" + id);
    }

    private void check(boolean unclassified) {
        Long id = jdbc.queryForObject("SELECT m.material_id FROM inventory_material m JOIN inventory_material_category c ON c.category_id=m.category_id WHERE c.category_code='RAW_BASE_PC' ORDER BY m.material_id LIMIT 1", Long.class);
        Map<String, Object> original = row(id);
        new TransactionTemplate(manager).executeWithoutResult(tx -> {
            tx.setRollbackOnly();
            // 仅在回滚事务内构造未归类及分类树拦截场景，不改持久数据。
            if (unclassified) jdbc.update("UPDATE inventory_material SET category_id=NULL WHERE material_id=?", id);
            Map<String, Object> before = row(id);
            var stockBefore = jdbc.queryForList("SELECT * FROM inventory_stock WHERE material_id=? ORDER BY stock_id", id);
            var batchesBefore = jdbc.queryForList("SELECT * FROM inventory_stock_item WHERE material_id=? ORDER BY item_id", id);
            var saved = service.updateNaming(id, new MaterialMasterService.NamingPatch("P0命名事务验证", "P0规格事务验证", "事务回滚用例"), "codex-test");
            assertEquals("P0命名事务验证", saved.material().getMaterialName());
            Map<String, Object> after = row(id);
            assertNotEquals("P0规格事务验证", after.get("specification"), "直接传入的规格必须忽略");
            if (unclassified) {
                assertEquals(before.get("specification"), after.get("specification"));
                assertFalse(saved.specificationGeneration().generated());
                assertTrue(saved.specificationGeneration().message().contains("未生成"));
            } else {
                assertTrue(saved.specificationGeneration().generated());
                assertEquals(service.specificationPreview(saved.material()).specification(), after.get("specification"));
            }
            assertEquals("codex-test", after.get("update_by"));
            assertNotNull(after.get("update_time"));
            // search_blob 是数据库生成的检索列，名称/规格变动时应同步派生。
            assertTrue(after.get("search_blob").toString().contains("P0命名事务验证"));
            assertFalse(after.get("search_blob").toString().contains("P0规格事务验证"));
            assertEquals(protectedColumns(before), protectedColumns(after));
            assertEquals(stockBefore, jdbc.queryForList("SELECT * FROM inventory_stock WHERE material_id=? ORDER BY stock_id", id));
            assertEquals(batchesBefore, jdbc.queryForList("SELECT * FROM inventory_stock_item WHERE material_id=? ORDER BY item_id", id));
            System.out.println("NAMING " + (unclassified ? "unclassified" : "classified") + " id=" + id
                    + " before=" + evidence(before) + " after=" + evidence(after)
                    + " specificationBefore=" + before.get("specification") + " specificationAfter=" + after.get("specification")
                    + " allOtherBusinessColumnsEqual=true stockAndBatchesUnchanged=true");
            if (!unclassified) {
                var patch = new InventoryMaterial(); patch.setMaterialId(id); patch.setMaterialName("旧接口反例");
                assertEquals("该品类请使用新版物料保存入口", assertThrows(BusinessException.class,
                        () -> legacy.update(patch, null, "codex-test")).getMessage());
                assertEquals(after, row(id));
            }
        });
        assertEquals(original, row(id));
        System.out.println("NAMING rollback verified id=" + id);
    }

    @Test void saveFollowsChangedAttributesAndClearingRemovesObsoleteParts() {
        Long id = materialId();
        Map<String, Object> original = row(id);
        new TransactionTemplate(manager).executeWithoutResult(tx -> {
            tx.setRollbackOnly();
            var dto = form(id);
            dto.setWidthMm(new java.math.BigDecimal("300")); dto.setLengthMm(new java.math.BigDecimal("305")); dto.setThicknessMm(null); dto.setSizeUnit("mm");
            dto.setThicknessMm(new java.math.BigDecimal("0.175")); dto.setAttributeJson(Map.of("surface_treatment", List.of("MEDIUM_SAND")));
            var before = service.save(dto, "codex-test");
            assertEquals("表面处理:中砂；尺寸:300*305*0.175mm", before.getSpecification());
            dto.setThicknessMm(new java.math.BigDecimal("0.25")); dto.setAttributeJson(Map.of());
            var changed = service.save(dto, "codex-test");
            assertEquals("尺寸:300*305*0.25mm", changed.getSpecification());
            dto.setAttributeJson(Map.of()); dto.setWidthMm(null); dto.setLengthMm(null); dto.setThicknessMm(null); dto.setSizeUnit("mm");
            var cleared = service.save(dto, "codex-test");
            assertNull(cleared.getSpecification()); assertNull(cleared.getThicknessMm()); assertNull(cleared.getWidthMm());
            System.out.println("SPEC_SAVE id=" + id + " before=" + before.getSpecification() + " changed="
                    + changed.getSpecification() + " cleared=" + cleared.getSpecification());
        });
        assertEquals(original, row(id));
        System.out.println("SPEC_SAVE rollback verified id=" + id);
    }

    @Test void missingOrEmptyTemplatePreservesHistoricalValueForNamingAndSave() {
        Long id = materialId();
        Map<String, Object> original = row(id);
        Long category = ((Number) original.get("category_id")).longValue();
        var templateBefore = jdbc.queryForMap("SELECT * FROM inventory_attribute_template WHERE category_id=?", category);
        new TransactionTemplate(manager).executeWithoutResult(tx -> {
            tx.setRollbackOnly();
            jdbc.update("UPDATE inventory_material SET specification='历史有效规格' WHERE material_id=?", id);
            for (boolean empty : List.of(false, true)) {
                if (empty) jdbc.update("UPDATE inventory_attribute_template SET status=1,attribute_schema=JSON_SET(attribute_schema,'$.fields',JSON_ARRAY()) WHERE category_id=?", category);
                else jdbc.update("UPDATE inventory_attribute_template SET status=0 WHERE category_id=?", category);
                var before = row(id);
                var naming = service.updateNaming(id, new MaterialMasterService.NamingPatch("保留历史规格", "伪造规格", null), "codex-test");
                assertEquals("历史有效规格", naming.material().getSpecification());
                assertFalse(naming.specificationGeneration().generated());
                assertEquals(protectedColumns(before), protectedColumns(row(id)));
                var dto = form(id); dto.setAttributeJson(Map.of());
                assertEquals("历史有效规格", service.save(dto, "codex-test").getSpecification());
                assertTrue(service.specificationPreview(naming.material()).message().contains("未生成"));
            }
        });
        assertEquals(original, row(id));
        assertEquals(templateBefore, jdbc.queryForMap("SELECT * FROM inventory_attribute_template WHERE category_id=?", category));
        System.out.println("SPEC_NO_TEMPLATE and SPEC_EMPTY_TEMPLATE rollback verified id=" + id);
    }

    @Test void unclassifiedFullSaveAlsoRetainsHistoricalSpecification() {
        Long id = materialId();
        Map<String, Object> original = row(id);
        new TransactionTemplate(manager).executeWithoutResult(tx -> {
            tx.setRollbackOnly();
            jdbc.update("UPDATE inventory_material SET category_id=NULL,specification='未归类历史规格' WHERE material_id=?", id);
            var dto = new MaterialMasterDTO(); dto.setMaterialId(id); dto.setMaterialName("未归类保存");
            dto.setUnit((String) original.get("unit")); dto.setAttributeJson(Map.of());
            var saved = service.save(dto, "codex-test");
            assertNull(saved.getCategoryId());
            assertEquals("未归类历史规格", saved.getSpecification());
            assertFalse(service.specificationPreview(saved).generated());
        });
        assertEquals(original, row(id));
        System.out.println("SPEC_UNCLASSIFIED_SAVE rollback verified id=" + id);
    }

    @Test void httpNamingAndSaveIgnoreExternalSpecificationAgainstRealDatabase() throws Exception {
        Long id = materialId();
        Map<String, Object> original = row(id);
        var controller = new MaterialMasterController(catalog, service, null, null, null);
        var mvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(json)).build();
        try (var stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getSession).thenReturn(new SaSession("codex-test"));
            new TransactionTemplate(manager).executeWithoutResult(tx -> {
                tx.setRollbackOnly();
                try {
                    String content = json.writeValueAsString(Map.of("materialId", id, "categoryId", original.get("category_id"),
                            "materialName", "HTTP规格事务验证", "attributeJson", Map.of(),
                            "widthMm", 500, "thicknessMm", 0.5, "sizeUnit", "mm", "specification", "外部伪造规格"));
                    var saved = mvc.perform(put("/inventory/material/master").contentType(MediaType.APPLICATION_JSON).content(content)).andReturn().getResponse();
                    assertEquals(200, saved.getStatus());
                    var body = json.readTree(saved.getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                    assertEquals(200, body.get("code").asInt());
                    assertEquals("尺寸:500**0.5mm", body.at("/data/specification").asText());
                    assertTrue(body.get("msg").asText().contains("不接受外部 specification"));
                    var naming = mvc.perform(put("/inventory/material/master/" + id + "/naming")
                            .contentType(MediaType.APPLICATION_JSON).content("{\"materialName\":\"HTTP命名\",\"specification\":\"外部伪造规格\"}"))
                            .andReturn().getResponse();
                    assertEquals(200, naming.getStatus());
                    var result = json.readTree(naming.getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
                    assertEquals("尺寸:500**0.5mm", result.at("/data/material/specification").asText());
                    assertTrue(result.at("/data/specificationGeneration/generated").asBoolean());
                    assertFalse(row(id).get("specification").toString().contains("伪造"));
                    System.out.println("SPEC_HTTP save=" + body.at("/data/specification") + " naming=" + result.at("/data/material/specification") + " externalIgnored=true");
                } catch (Exception error) { throw new AssertionError(error); }
            });
        }
        assertEquals(original, row(id));
        System.out.println("SPEC_HTTP rollback verified id=" + id);
    }

    private static Long materialId() {
        return jdbc.queryForObject("SELECT m.material_id FROM inventory_material m JOIN inventory_material_category c ON c.category_id=m.category_id WHERE c.category_code='RAW_BASE_PC' ORDER BY m.material_id LIMIT 1", Long.class);
    }
    private static MaterialMasterDTO form(Long id) {
        var dto = new MaterialMasterDTO(); dto.setMaterialId(id);
        dto.setCategoryId(((Number) row(id).get("category_id")).longValue());
        dto.setMaterialName("规格事务测试"); dto.setAttributeJson(Map.of());
        return dto;
    }

    private static Map<String, Object> row(Long id) { return jdbc.queryForMap("SELECT * FROM inventory_material WHERE material_id=?", id); }
    private static Map<String, Object> protectedColumns(Map<String, Object> row) {
        var result = new LinkedHashMap<>(row);
        for (String column : new String[]{"material_name", "specification", "update_by", "update_time", "search_blob"}) result.remove(column);
        return result;
    }
    private static Map<String, Object> evidence(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String column : new String[]{"category_id", "template_id", "supplier_id", "safe_stock", "max_stock", "reorder_point", "material_code"}) result.put(column, row.get(column));
        return result;
    }
}
