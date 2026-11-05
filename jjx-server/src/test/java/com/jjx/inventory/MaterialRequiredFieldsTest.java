package com.jjx.inventory;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.domain.InventoryMaterialCategory;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.inventory.master.*;
import com.jjx.inventory.service.InventoryMaterialService;
import com.jjx.purchase.mapper.PurchaseSupplierMapper;
import com.jjx.system.config.GlobalExceptionHandler;
import com.jjx.system.service.ISysTagService;
import com.jjx.system.service.LogSaveService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 隔离 H2 MySQL 模式验证接口、事务和迁移幂等性，不连接或修改业务库。 */
class MaterialRequiredFieldsTest {
    private final ObjectMapper json = new ObjectMapper();
    private JdbcTemplate jdbc;
    private DataSourceTransactionManager transactions;
    private InventoryMaterialMapper materials;
    private MaterialMasterMapper locks;
    private MaterialMasterCatalog catalog;
    private MaterialMasterService service;

    @BeforeEach void setup() throws Exception {
        var source = new DriverManagerDataSource("jdbc:h2:mem:required_" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        transactions = new DataSourceTransactionManager(source);
        jdbc.execute("CREATE TABLE inventory_material_category (category_id BIGINT PRIMARY KEY, category_code VARCHAR(50), category_name VARCHAR(100), category_path VARCHAR(200), name_path VARCHAR(200), parent_id BIGINT, remark VARCHAR(200))");
        jdbc.execute("CREATE TABLE inventory_material_required_field (id BIGINT AUTO_INCREMENT PRIMARY KEY, category_code VARCHAR(50), field_key VARCHAR(50), required TINYINT, UNIQUE(category_code, field_key))");
        jdbc.update("INSERT INTO inventory_material_category(category_id,category_code,category_name,parent_id) VALUES (23,'RAW_AUX_PROTECT','保护膜',17)");
        var config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.addMapper(InventoryMaterialMapper.class);
        var factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(source); factory.setConfiguration(config);
        materials = spy(new SqlSessionTemplate(factory.getObject()).getMapper(InventoryMaterialMapper.class));
        locks = mock(MaterialMasterMapper.class);
        when(locks.lockCategory(anyLong())).thenReturn(null);
        when(locks.lockCategory(23L)).thenReturn(23L);
        catalog = mock(MaterialMasterCatalog.class);
        service = new MaterialMasterService(catalog, locks, materials, mock(InventoryMaterialService.class),
                mock(PurchaseSupplierMapper.class), mock(ISysTagService.class), null, json);
        service = proxy(service);
    }

    @SuppressWarnings("unchecked") private <T> T proxy(T target) {
        var factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        return (T) factory.getProxy();
    }

    private Map<String, Object> fields(boolean color, boolean brand) {
        return new LinkedHashMap<>(Map.of("material_grade", false, "model", false, "color", color, "specification", false, "brand", brand));
    }
    private MaterialMasterController controller(MaterialMasterConfigurationService configuration) {
        return new MaterialMasterController(catalog, service, configuration, null, null, null);
    }

    @Test void httpReadsAndWritesFiveBooleansAndRejectsUnknownMissingNullOrNonBooleanKeys() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(controller(null))
                .setControllerAdvice(new GlobalExceptionHandler(mock(LogSaveService.class))).build();
        String url = "/inventory/material/master/categories/23/required-fields";
        mvc.perform(get(url)).andExpect(jsonPath("$.data.color").value(false));
        mvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(fields(true, false))))
                .andExpect(jsonPath("$.code").value(200));
        mvc.perform(get(url)).andExpect(jsonPath("$.data.color").value(true)).andExpect(jsonPath("$.data.brand").value(false));
        var invalid = new ArrayList<Map<String, Object>>();
        var unknown = fields(false, false); unknown.put("unit", true); invalid.add(unknown);
        var missing = fields(false, false); missing.remove("color"); invalid.add(missing);
        for (Object value : Arrays.asList(null, "true", 1)) {
            var body = fields(false, false); body.put("color", value); invalid.add(body);
        }
        for (var body : invalid) {
            mvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                    .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.not(200)));
            assertEquals(true, service.requiredFields(23L).get("color"));
        }
        assertThrows(BusinessException.class, () -> service.saveRequiredFields(999L, fields(true, true)));
        assertThrows(BusinessException.class, () -> service.requiredFields(999L));
        assertEquals(5, jdbc.queryForObject("SELECT COUNT(*) FROM inventory_material_required_field", Integer.class));
    }

    @Test void usesOnlyCategoryListAndEditPermissions() throws Exception {
        StpLogic original = StpUtil.getStpLogic();
        try {
            var logic = spy(new StpLogic("login"));
            doReturn(42L).when(logic).getLoginId();
            StpUtil.setStpLogic(logic);
            var mvc = MockMvcBuilders.standaloneSetup(controller(null)).addInterceptors(new SaInterceptor())
                    .setControllerAdvice(new GlobalExceptionHandler(mock(LogSaveService.class))).build();
            String url = "/inventory/material/master/categories/23/required-fields";
            doReturn(List.of("inventory:category:list")).when(logic).getPermissionList(any());
            mvc.perform(get(url)).andExpect(jsonPath("$.code").value(200));
            mvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(fields(true, true))))
                    .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.not(200)));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM inventory_material_required_field", Integer.class));
            doReturn(List.of("inventory:category:edit")).when(logic).getPermissionList(any());
            mvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(fields(true, true))))
                    .andExpect(jsonPath("$.code").value(200));
            mvc.perform(get(url)).andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.not(200)));
        } finally { StpUtil.setStpLogic(original); }
    }

    @Test void changingConfigurationAffectsOnlyCreatesAndRetainsBomColorExemption() {
        var category = new MaterialMasterCatalog.Category(23L, "RAW_AUX_PROTECT", "保护膜", 17L, 3, "R", "原材料/保护膜", true, 1, 1);
        var template = new InventoryAttributeTemplate(); template.setTemplateId(23L);
        var schema = MaterialAttributeSchema.parse("{\"version\":1,\"meta_options\":[\"PC\"],\"default_meta\":\"PC\",\"default_unit\":\"PCS\",\"fields\":[]}", json);
        when(catalog.optionalConfiguration(23L)).thenReturn(new MaterialMasterCatalog.Configuration(category, List.of(category), template, schema));
        when(catalog.dictionaries()).thenReturn(Map.of("material_meta", List.of(new MaterialMasterCatalog.DictionaryOption("PC", "PC")),
                "unit", List.of(new MaterialMasterCatalog.DictionaryOption("PCS", "件"))));
        var dto = new MaterialMasterDTO(); dto.setCategoryId(23L); dto.setMaterialName("已有名称"); dto.setUnit("PCS");
        service.saveRequiredFields(23L, fields(true, false));
        assertEquals("颜色不能为空（保护膜 必填）", assertThrows(BusinessException.class, () -> service.prepare(dto)).getMessage());
        service.saveRequiredFields(23L, fields(false, false));
        assertDoesNotThrow(() -> service.prepare(dto));
        service.saveRequiredFields(23L, fields(false, true));
        assertEquals("品牌不能为空（保护膜 必填）", assertThrows(BusinessException.class, () -> service.prepare(dto)).getMessage());
        var old = new InventoryMaterial(); old.setMaterialId(100L); old.setCategoryId(23L);
        doReturn(old).when(materials).selectById(100L);
        dto.setMaterialId(100L);
        assertDoesNotThrow(() -> service.prepare(dto));
        dto.setMaterialId(null); dto.setFromBom(true);
        service.saveRequiredFields(23L, fields(true, false));
        assertDoesNotThrow(() -> service.prepare(dto));
        service.saveRequiredFields(23L, fields(true, true));
        assertEquals("品牌不能为空（保护膜 必填）", assertThrows(BusinessException.class, () -> service.prepare(dto)).getMessage());
    }

    @Test void categoryAndRequiredFieldsCommitTogetherAndRollbackTogetherOnFailure() {
        service.saveRequiredFields(23L, fields(true, false));
        var configuration = mock(MaterialMasterConfigurationService.class);
        when(configuration.saveCategory(any(), anyString())).thenAnswer(invocation -> {
            jdbc.update("UPDATE inventory_material_category SET category_name='更新保护膜' WHERE category_id=23");
            var category = new InventoryMaterialCategory(); category.setCategoryId(23L); return category;
        });
        var controller = proxy(controller(configuration));
        try (var stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getSession).thenReturn(new SaSession("codex-test"));
            var request = new MaterialCategorySaveDTO(23L, "RAW_AUX_PROTECT", "更新保护膜", 17L, 1, 1, "PF", "备注", fields(false, true));
            controller.saveCategory(request);
            assertEquals("更新保护膜", jdbc.queryForObject("SELECT category_name FROM inventory_material_category WHERE category_id=23", String.class));
            assertFalse(service.requiredFields(23L).get("color")); assertTrue(service.requiredFields(23L).get("brand"));
            jdbc.update("UPDATE inventory_material_category SET category_name='保护膜' WHERE category_id=23");
            service.saveRequiredFields(23L, fields(true, false));
            // 最后写备注失败时，前面已写入的基础信息与五项勾选全部回滚。
            var invalidRemark = new MaterialCategorySaveDTO(23L, "RAW_AUX_PROTECT", "更新保护膜", 17L, 1, 1, "PF", "x".repeat(201), fields(false, true));
            assertThrows(BusinessException.class, () -> controller.saveCategory(invalidRemark));
            assertEquals("保护膜", jdbc.queryForObject("SELECT category_name FROM inventory_material_category WHERE category_id=23", String.class));
            assertTrue(service.requiredFields(23L).get("color")); assertFalse(service.requiredFields(23L).get("brand"));
        }
    }

    @Test void migration347IsIdempotentAcrossAllCategoriesAndPreservesReleaseBrandConclusion() throws Exception {
        var codes = List.of("RAW", "SEMI", "FINISHED", "RAW_BASE_PET", "RAW_AUX_RELEASE", "RAW_AUX_RELEASE_FILM", "RAW_AUX_RELEASE_PAPER", "RAW_AUX_TAPE", "RAW_AUX_ELECTRONIC",
                "RAW_AUX_ELECTRONIC_LED", "RAW_AUX_ELECTRONIC_PIN", "RAW_AUX_ELECTRONIC_HOUSING", "RAW_AUX_ELECTRONIC_CONNECTOR", "RAW_AUX_ELECTRONIC_RESISTOR", "RAW_AUX_ELECTRONIC_CAPACITOR", "RAW_AUX_ELECTRONIC_DIODE", "RAW_AUX_ELECTRONIC_SPRING", "RAW_AUX_ELECTRONIC_HARDWARE", "RAW_AUX_ELECTRONIC_OTHER");
        long id = 100;
        for (String code : codes) jdbc.update("INSERT INTO inventory_material_category(category_id,category_code) VALUES (?,?)", id++, code);
        service.saveRequiredFields(23L, fields(false, true));
        String sql = Files.readString(Path.of("../jjx-docs/sql/migrations/347_material_required_fields_unify.sql"));
        jdbc.execute(sql);
        var first = jdbc.queryForList("SELECT category_code,field_key,required FROM inventory_material_required_field ORDER BY category_code,field_key");
        jdbc.execute(sql);
        assertEquals(first, jdbc.queryForList("SELECT category_code,field_key,required FROM inventory_material_required_field ORDER BY category_code,field_key"));
        assertEquals((codes.size() + 1) * 5, first.size());
        for (var row : first) {
            String code = (String) row.get("CATEGORY_CODE"), key = (String) row.get("FIELD_KEY");
            boolean model = code.equals("RAW_AUX_PROTECT") || code.equals("RAW_AUX_TAPE") || code.startsWith("RAW_AUX_RELEASE_") || code.startsWith("RAW_AUX_ELECTRONIC");
            boolean brand = code.equals("RAW_AUX_TAPE") || code.startsWith("RAW_AUX_ELECTRONIC");
            boolean required = switch (key) { case "model" -> model; case "brand" -> brand; default -> true; };
            assertEquals(required ? 1 : 0, ((Number) row.get("REQUIRED")).intValue(), code + "/" + key);
        }
    }
}
