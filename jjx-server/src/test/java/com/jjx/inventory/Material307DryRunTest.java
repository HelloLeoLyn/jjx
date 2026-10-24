package com.jjx.inventory;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.inventory.master.MaterialMasterMapper;
import com.jjx.inventory.util.MaterialClassification;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** 只连接用户指定的307演练库；真实Mapper读写，写入整段回滚。 */
@EnabledIfEnvironmentVariable(named = "JJX_TEST_307", matches = "1")
class Material307DryRunTest {
    @Test void migratedSchemaSupportsMaterialQueriesAndNullClassification() throws Exception {
        var source = new DriverManagerDataSource(
                "jdbc:mysql://127.0.0.1:3306/jjx_dryrun_307?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",
                System.getenv().getOrDefault("JJX_TEST_MYSQL_USER", "root"),
                System.getenv().getOrDefault("JJX_TEST_MYSQL_PASSWORD", "123456"));
        var config = new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true);
        config.setEnvironment(new org.apache.ibatis.mapping.Environment("307-dryrun",
                new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(), source));
        com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils.setGlobalConfig(config,
                new com.baomidou.mybatisplus.core.config.GlobalConfig().setDbConfig(new com.baomidou.mybatisplus.core.config.GlobalConfig.DbConfig()));
        config.addMapper(InventoryMaterialMapper.class); config.addMapper(MaterialMasterMapper.class);
        var factory = new com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder().build(config);
        try (var session = factory.openSession(false)) {
            try {
                var mapper = session.getMapper(InventoryMaterialMapper.class);
                var master = session.getMapper(MaterialMasterMapper.class);
                var all = mapper.selectList(null);
                assertEquals(1607, all.size());
                assertEquals(566, all.stream().filter(m -> m.getCategoryId() == null).count());
                var material = all.stream().filter(m -> m.getCategoryId() != null).findFirst().orElseThrow();
                assertNotNull(mapper.selectById(material.getMaterialId()));
                assertNotNull(mapper.selectByCode(material.getMaterialCode()));
                assertNotNull(master.lockMaterial(material.getMaterialId()));
                assertNotNull(master.firstMaterialInCategory(material.getCategoryId()));
                assertNotNull(mapper.selectTemplateId(material.getCategoryId()));
                assertEquals("R", mapper.materialTypeOf(material.getCategoryId()));
                assertNull(mapper.materialTypeOf(null));
                var raw = mapper.selectList(MaterialClassification.filterType(new LambdaQueryWrapper<>(), "R"));
                assertEquals(1041, raw.size());
                assertTrue(mapper.selectList(MaterialClassification.filterType(new LambdaQueryWrapper<>(), "S")).isEmpty());
                assertTrue(mapper.selectList(MaterialClassification.filterType(new LambdaQueryWrapper<>(), "F")).isEmpty());
                var searched = mapper.selectList(new LambdaQueryWrapper<InventoryMaterial>().apply(
                        "search_blob LIKE {0} OR EXISTS (SELECT 1 FROM inventory_material_category c WHERE c.category_id=inventory_material.category_id AND c.name_path LIKE {0})", "%原材料%"));
                assertEquals(1041, searched.size());
                // 未归类编辑不会补类型、改分类或重编历史AUX码。
                var unclassified = all.stream().filter(m -> m.getCategoryId() == null && m.getMaterialCode().startsWith("AUX")).findFirst().orElseThrow();
                assertEquals(1, mapper.update(null, new UpdateWrapper<InventoryMaterial>()
                        .eq("material_id", unclassified.getMaterialId()).set("remark", "307事务内验证")));
                session.clearCache();
                var read = mapper.selectById(unclassified.getMaterialId());
                assertNull(read.getCategoryId()); assertEquals(unclassified.getMaterialCode(), read.getMaterialCode());
                // 新增/更新SQL均不包含六个已删列；不调用编号服务或库存写入。
                var created = new InventoryMaterial(); created.setMaterialCode("T307-ROLLBACK");
                created.setMaterialName("307演练"); created.setUnit("PCS");
                created.setCreateBy("codex-dryrun"); created.setUpdateBy("codex-dryrun");
                assertEquals(1, mapper.insert(created));
                assertNull(mapper.selectById(created.getMaterialId()).getCategoryId());
                try (var statement = session.getConnection().createStatement()) {
                    try (var count = statement.executeQuery("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_material'")) {
                        assertTrue(count.next()); assertEquals(48, count.getInt(1));
                    }
                    // 已有成本报表取价SQL不受六列删除影响。
                    statement.executeQuery("SELECT COALESCE(NULLIF(m.cost_price,0),NULLIF(m.standard_price,0)) FROM inventory_material m WHERE (m.material_id=1 OR m.material_code='RM000001') LIMIT 1").close();
                    statement.executeQuery("SELECT m.material_id,SUM(s.total_quantity) FROM inventory_material m LEFT JOIN inventory_stock s ON s.material_id=m.material_id GROUP BY m.material_id LIMIT 5").close();
                }
                // 执行出库服务里的真实SQL，防止测试仅验证另写的等价查询。
                String outbound = Files.readString(Path.of("src/main/java/com/jjx/inventory/service/impl/InventoryOutboundServiceImpl.java"));
                var sql = java.util.regex.Pattern.compile("\"(SELECT m\\.material_id FROM inventory_material m JOIN inventory_material_category c[^\"]+)\"").matcher(outbound);
                assertTrue(sql.find());
                try (var statement = session.getConnection().prepareStatement(sql.group(1))) {
                    statement.setString(1, material.getMaterialName()); statement.executeQuery().close();
                }
                System.out.println("DRYRUN_307 Mapper/检索/类型筛选/半成品SQL/成本报表/库存聚合/未归类编辑/新增：通过；事务ROLLBACK");
            } finally { session.rollback(true); }
        }
        try (var connection = source.getConnection(); var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT COUNT(*) FROM inventory_material WHERE material_code='T307-ROLLBACK' OR remark='307事务内验证'")) {
            assertTrue(result.next()); assertEquals(0, result.getInt(1));
        }
    }
}
