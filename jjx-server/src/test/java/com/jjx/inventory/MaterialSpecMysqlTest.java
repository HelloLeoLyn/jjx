package com.jjx.inventory;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.jjx.common.exception.BusinessException;
import com.jjx.inventory.dto.update.MaterialSpecValuesDTO;
import com.jjx.inventory.dto.vo.MaterialSpecValuesVO;
import com.jjx.inventory.mapper.InventoryMaterialCategoryMapper;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.inventory.mapper.InventoryMaterialSpecAttrDefMapper;
import com.jjx.inventory.mapper.InventoryMaterialSpecValueMapper;
import com.jjx.inventory.service.InventoryMaterialSpecService;
import com.jjx.inventory.service.impl.InventoryMaterialSpecServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 规格属性服务真实库验证（dev-20261023-022）：真实 MySQL + Mapper + 事务代理，每项回滚，不启服务。
 * 判据：属性组合写回 specification；属性明细落 spec_value；属性全空不清空历史文本；
 * 未配置属性的品类拒绝写入且保留自由填写。
 */
@EnabledIfEnvironmentVariable(named = "JJX_TEST_MYSQL", matches = "1")
class MaterialSpecMysqlTest {

    private static DataSourceTransactionManager manager;
    private static JdbcTemplate jdbc;
    private static InventoryMaterialSpecService specService;
    private TransactionStatus transaction;

    @BeforeAll
    static void setupDatabase() throws Exception {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
        ds.setUrl(System.getenv().getOrDefault("JJX_TEST_MYSQL_URL",
                "jdbc:mysql://127.0.0.1:3306/jjx_erp_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"));
        ds.setUsername(System.getenv().getOrDefault("DB_USER", "root"));
        ds.setPassword(System.getenv().getOrDefault("DB_PASS", "123456"));
        manager = new DataSourceTransactionManager(ds);
        jdbc = new JdbcTemplate(ds);

        MybatisConfiguration config = new MybatisConfiguration();
        config.setMapUnderscoreToCamelCase(true);
        config.addMapper(InventoryMaterialSpecAttrDefMapper.class);
        config.addMapper(InventoryMaterialSpecValueMapper.class);
        config.addMapper(InventoryMaterialMapper.class);
        config.addMapper(InventoryMaterialCategoryMapper.class);
        MybatisSqlSessionFactoryBean bean = new MybatisSqlSessionFactoryBean();
        bean.setDataSource(ds);
        bean.setConfiguration(config);
        bean.setGlobalConfig(new GlobalConfig().setBanner(false));
        SqlSessionTemplate session = new SqlSessionTemplate(bean.getObject());

        InventoryMaterialSpecServiceImpl impl = new InventoryMaterialSpecServiceImpl(
                session.getMapper(InventoryMaterialSpecAttrDefMapper.class),
                session.getMapper(InventoryMaterialSpecValueMapper.class),
                session.getMapper(InventoryMaterialMapper.class),
                session.getMapper(InventoryMaterialCategoryMapper.class));
        ProxyFactory factory = new ProxyFactory(impl);
        factory.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        specService = (InventoryMaterialSpecService) factory.getProxy();
    }

    @BeforeEach
    void begin() {
        transaction = manager.getTransaction(new DefaultTransactionDefinition());
    }

    @AfterEach
    void rollback() {
        if (transaction != null && !transaction.isCompleted()) {
            manager.rollback(transaction);
        }
    }

    /** 直接插一行物料（回滚内），返回物料ID。 */
    private Long material(String code, long categoryId, String specification) {
        String materialCode = code + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        jdbc.update("INSERT INTO inventory_material (material_code, material_name, material_type, category_id, "
                        + "specification, unit, create_by, update_by) VALUES (?,?,?,?,?,?,?,?)",
                materialCode, "规格属性测试" + materialCode, "R", categoryId, specification, "PCS", "test", "test");
        return jdbc.queryForObject("SELECT material_id FROM inventory_material WHERE material_code=?",
                Long.class, materialCode);
    }

    private static MaterialSpecValuesDTO.Item item(String attrCode, String value, String value2) {
        MaterialSpecValuesDTO.Item i = new MaterialSpecValuesDTO.Item();
        i.setAttrCode(attrCode);
        i.setValue(value);
        i.setValue2(value2);
        return i;
    }

    @Test
    void composesAndPersistsAttributesForFilm() {
        Long id = material("T022A", 2L, null); // 2 = RM-FILM
        MaterialSpecValuesDTO dto = new MaterialSpecValuesDTO();
        dto.setMaterialId(id);
        dto.setCategoryCode("RM-FILM");
        dto.setCreateMode(true);
        dto.setSupplement("客供");
        dto.setAttributes(List.of(item("thickness", "0.38", null),
                item("surface", "幼砂加硬", null), item("adhesive_side", "双面", null)));

        MaterialSpecValuesVO vo = specService.save(dto);

        assertEquals("0.38mm 幼砂加硬 双面；客供", vo.getSpecification());
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT specification, spec_supplement FROM inventory_material WHERE material_id=?", id);
        assertEquals("0.38mm 幼砂加硬 双面；客供", row.get("specification"));
        assertEquals("客供", row.get("spec_supplement"));
        assertEquals(3, jdbc.queryForObject(
                "SELECT COUNT(*) FROM inventory_material_spec_value WHERE material_id=?", Integer.class, id));
        assertEquals(0, new BigDecimal("0.38").compareTo(jdbc.queryForObject(
                "SELECT value_num FROM inventory_material_spec_value WHERE material_id=? AND attr_code='thickness'",
                BigDecimal.class, id)));
        assertEquals("幼砂加硬", jdbc.queryForObject(
                "SELECT value_text FROM inventory_material_spec_value WHERE material_id=? AND attr_code='surface'",
                String.class, id));
        assertNotNull(vo.getDefs());
        assertTrue(vo.getDefs().size() >= 3);
    }

    @Test
    void keepsLegacySpecTextWhenAttributesAreEmpty() {
        Long id = material("T022B", 2L, "旧口径 250×3.5m");
        MaterialSpecValuesDTO dto = new MaterialSpecValuesDTO();
        dto.setMaterialId(id);
        dto.setCategoryCode("RM-FILM");
        dto.setCreateMode(false);
        dto.setAttributes(List.of());

        MaterialSpecValuesVO vo = specService.save(dto);

        // 反例保护：编辑历史物料（无属性值）不能把已有规格清空
        assertEquals("旧口径 250×3.5m", vo.getSpecification());
        assertEquals("旧口径 250×3.5m", jdbc.queryForObject(
                "SELECT specification FROM inventory_material WHERE material_id=?", String.class, id));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM inventory_material_spec_value WHERE material_id=?", Integer.class, id));
    }

    @Test
    void rejectsMissingRequiredAttributeOnCreate() {
        Long id = material("T022C", 2L, null);
        MaterialSpecValuesDTO dto = new MaterialSpecValuesDTO();
        dto.setMaterialId(id);
        dto.setCategoryCode("RM-FILM");
        dto.setCreateMode(true);
        dto.setAttributes(List.of(item("surface", "光面", null)));

        BusinessException error = assertThrows(BusinessException.class, () -> specService.save(dto));
        assertTrue(error.getMessage().contains("厚度"), error.getMessage());
        // 校验先于写入：失败时不留半条属性
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM inventory_material_spec_value WHERE material_id=?", Integer.class, id));
    }

    @Test
    void rejectsIllegalOptionWithoutPartialWrite() {
        Long id = material("T022D", 2L, null);
        MaterialSpecValuesDTO dto = new MaterialSpecValuesDTO();
        dto.setMaterialId(id);
        dto.setCategoryCode("RM-FILM");
        dto.setAttributes(List.of(item("thickness", "0.38", null), item("surface", "磨砂", null)));

        assertThrows(BusinessException.class, () -> specService.save(dto));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM inventory_material_spec_value WHERE material_id=?", Integer.class, id));
    }

    @Test
    void emptyCategoryKeepsFreeTextPath() {
        Long id = material("T022E", 10L, "手工填写的历史规格"); // 10 = OTHER-R，未配置属性
        MaterialSpecValuesDTO dto = new MaterialSpecValuesDTO();
        dto.setMaterialId(id);
        dto.setCreateMode(false);
        dto.setAttributes(List.of());

        MaterialSpecValuesVO vo = specService.save(dto);
        assertTrue(vo.getDefs().isEmpty());
        assertEquals("手工填写的历史规格", vo.getSpecification());

        // 反例：未配置属性的品类不允许传属性值，否则会悄悄覆盖自由文本
        MaterialSpecValuesDTO withAttrs = new MaterialSpecValuesDTO();
        withAttrs.setMaterialId(id);
        withAttrs.setAttributes(List.of(item("thickness", "0.38", null)));
        BusinessException error = assertThrows(BusinessException.class, () -> specService.save(withAttrs));
        assertTrue(error.getMessage().contains("未配置规格属性"), error.getMessage());
        assertEquals("手工填写的历史规格", jdbc.queryForObject(
                "SELECT specification FROM inventory_material WHERE material_id=?", String.class, id));
    }
}
