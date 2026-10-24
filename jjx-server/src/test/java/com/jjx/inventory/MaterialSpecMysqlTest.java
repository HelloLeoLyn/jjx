package com.jjx.inventory;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.jjx.common.exception.BusinessException;
import com.jjx.inventory.dto.update.MaterialSpecValuesDTO;
import com.jjx.inventory.dto.vo.MaterialSpecValuesVO;
import com.jjx.inventory.mapper.InventoryMaterialCategoryMapper;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
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

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 规格属性服务真实库验证（dev-20261023-022）：真实 MySQL + Mapper + 事务代理，每项回滚，不启服务。
 * 阶段A判据：从attribute_json回显，旧保存接口拒绝写入，302历史表和规格文本保持不变。
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
        config.addMapper(InventoryMaterialMapper.class);
        config.addMapper(InventoryMaterialCategoryMapper.class);
        MybatisSqlSessionFactoryBean bean = new MybatisSqlSessionFactoryBean();
        bean.setDataSource(ds);
        bean.setConfiguration(config);
        bean.setGlobalConfig(new GlobalConfig().setBanner(false));
        SqlSessionTemplate session = new SqlSessionTemplate(bean.getObject());

        InventoryMaterialSpecServiceImpl impl = new InventoryMaterialSpecServiceImpl(
                session.getMapper(InventoryMaterialMapper.class),
                session.getMapper(InventoryMaterialCategoryMapper.class), new com.fasterxml.jackson.databind.ObjectMapper());
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
    private Long material(String code, String specification) {
        String materialCode = code + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        jdbc.update("INSERT INTO inventory_material (material_code, material_name, category_id, "
                        + "specification, unit, create_by, update_by) VALUES (?,?,?,?,?,?,?)",
                materialCode, "规格属性测试" + materialCode, null, specification, "PCS", "test", "test");
        return jdbc.queryForObject("SELECT material_id FROM inventory_material WHERE material_code=?",
                Long.class, materialCode);
    }

    @Test
    void readsJsonAndRejectsLegacyWrites() {
        Long id = material("T007A", "历史规格");
        jdbc.update("UPDATE inventory_material SET attribute_json=? WHERE material_id=?", "{\"thickness_mm\":0.38}", id);
        long oldValues = jdbc.queryForObject("SELECT COUNT(*) FROM inventory_material_spec_value", Long.class);
        MaterialSpecValuesVO vo = specService.getByMaterialId(id);
        assertEquals("历史规格", vo.getSpecification());
        assertTrue(vo.getDefs().isEmpty());
        assertEquals("thickness_mm", vo.getValues().get(0).getAttrCode());
        assertEquals("0.38", vo.getValues().get(0).getValueText());
        var dto = new MaterialSpecValuesDTO(); dto.setMaterialId(id);
        assertTrue(assertThrows(BusinessException.class, () -> specService.save(dto)).getMessage().contains("已停用"));
        assertEquals(oldValues, jdbc.queryForObject("SELECT COUNT(*) FROM inventory_material_spec_value", Long.class));
        assertEquals("历史规格", jdbc.queryForObject("SELECT specification FROM inventory_material WHERE material_id=?", String.class, id));
    }
}
