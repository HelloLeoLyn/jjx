package com.jjx.inventory;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.event.EventPublisher;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.inventory.master.MaterialMasterMapper;
import com.jjx.inventory.master.MaterialMasterService;
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

    @BeforeAll static void setup() throws Exception {
        var source = new DriverManagerDataSource(System.getenv().getOrDefault("JJX_TEST_MYSQL_URL",
                "jdbc:mysql://127.0.0.1:3306/jjx_erp_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"),
                System.getenv().getOrDefault("DB_USER", "root"), System.getenv().getOrDefault("DB_PASS", "123456"));
        jdbc = new JdbcTemplate(source); manager = new DataSourceTransactionManager(source);
        var config = new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true);
        config.addMapper(InventoryMaterialMapper.class); config.addMapper(MaterialMasterMapper.class);
        var bean = new MybatisSqlSessionFactoryBean(); bean.setDataSource(source);
        bean.setConfiguration(config); bean.setGlobalConfig(new GlobalConfig().setBanner(false));
        var session = new SqlSessionTemplate(bean.getObject());
        var materials = session.getMapper(InventoryMaterialMapper.class);
        var target = new MaterialMasterService(null, session.getMapper(MaterialMasterMapper.class), materials,
                null, null, null, mock(EventPublisher.class), new ObjectMapper());
        var proxy = new ProxyFactory(target); proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        service = (MaterialMasterService) proxy.getProxy();
        legacy = new InventoryMaterialServiceImpl(materials, null, null, null, null, null, null, null, null, null, null);
    }

    @Test void classifiedNamingPreservesEveryOtherColumnAndOldSaveStillRejects() { check(false); }
    @Test void unclassifiedNamingAlsoPreservesEveryOtherColumn() { check(true); }

    private void check(boolean unclassified) {
        Long id = jdbc.queryForObject("SELECT m.material_id FROM inventory_material m JOIN inventory_material_category c ON c.category_id=m.category_id WHERE c.category_code='RAW' OR c.category_path LIKE 'RAW/%' ORDER BY m.material_id LIMIT 1", Long.class);
        Map<String, Object> original = row(id);
        new TransactionTemplate(manager).executeWithoutResult(tx -> {
            tx.setRollbackOnly();
            // 仅在回滚事务内构造未归类及分类树拦截场景，不改持久数据。
            if (unclassified) jdbc.update("UPDATE inventory_material SET category_id=NULL WHERE material_id=?", id);
            else jdbc.update("UPDATE inventory_material SET original_name=NULL WHERE material_id=?", id);
            Map<String, Object> before = row(id);
            var stockBefore = jdbc.queryForList("SELECT * FROM inventory_stock WHERE material_id=? ORDER BY stock_id", id);
            var batchesBefore = jdbc.queryForList("SELECT * FROM inventory_stock_item WHERE material_id=? ORDER BY item_id", id);
            var saved = service.updateNaming(id, new MaterialMasterService.NamingPatch("P0命名事务验证", "P0规格事务验证", "事务回滚用例"), "codex-test");
            assertEquals("P0命名事务验证", saved.material().getMaterialName());
            Map<String, Object> after = row(id);
            assertEquals("P0规格事务验证", after.get("specification")); assertEquals("codex-test", after.get("update_by"));
            assertNotNull(after.get("update_time"));
            // search_blob 是数据库生成的检索列，名称/规格变动时应同步派生。
            assertTrue(after.get("search_blob").toString().contains("P0命名事务验证"));
            assertTrue(after.get("search_blob").toString().contains("P0规格事务验证"));
            assertEquals(protectedColumns(before), protectedColumns(after));
            assertEquals(stockBefore, jdbc.queryForList("SELECT * FROM inventory_stock WHERE material_id=? ORDER BY stock_id", id));
            assertEquals(batchesBefore, jdbc.queryForList("SELECT * FROM inventory_stock_item WHERE material_id=? ORDER BY item_id", id));
            System.out.println("NAMING " + (unclassified ? "unclassified" : "classified") + " id=" + id
                    + " before=" + evidence(before) + " after=" + evidence(after) + " allOtherBusinessColumnsEqual=true stockAndBatchesUnchanged=true");
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
