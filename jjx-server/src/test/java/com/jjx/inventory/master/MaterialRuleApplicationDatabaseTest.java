package com.jjx.inventory.master;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.event.EventPublisher;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.system.mapper.SysOperLogMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** 真实MySQL事务验证；所有写入只落当前连接的临时表，不写业务表。 */
@EnabledIfEnvironmentVariable(named = "JJX_TEST_RULES_DATABASE", matches = "1")
class MaterialRuleApplicationDatabaseTest {
    @Test void selectedWritesCommitWithAuditAndAuditFailureRollsBackBothOnRealMysql() throws Exception {
        var source = new SingleConnectionDataSource(System.getenv().getOrDefault("JJX_TEST_MYSQL_URL",
                "jdbc:mysql://127.0.0.1:3306/jjx_erp_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"),
                System.getenv().getOrDefault("DB_USER", "root"), System.getenv().getOrDefault("DB_PASS", "123456"), true);
        try {
            var jdbc = new JdbcTemplate(source);
            // 从只读结构生成同名临时表；去掉临时表不支持的外键，连接关闭即销毁。
            for (String table : List.of("inventory_material", "inventory_attribute_template", "sys_oper_log")) {
                String ddl = jdbc.queryForMap("SHOW CREATE TABLE " + table).get("Create Table").toString();
                ddl = String.join("\n", ddl.lines().filter(line -> !line.contains("FOREIGN KEY")).toList())
                        .replaceFirst("CREATE TABLE", "CREATE TEMPORARY TABLE").replaceAll(",\\n\\)", "\n)");
                jdbc.execute(ddl);
            }
            jdbc.execute("CREATE TEMPORARY TABLE inventory_material_category (category_id BIGINT PRIMARY KEY)");
            jdbc.update("INSERT INTO inventory_material_category VALUES (26)");
            var json = new ObjectMapper().findAndRegisterModules();
            var schema = MaterialAttributeSchema.parse("""
                {"version":1,"meta_options":[],"fields":[],"name_pattern":{"fields":["model"],"separator":"-","empty":"skip","prefixes":{"model":"新名-"}},
                 "spec_pattern":{"fields":[{"key":"thickness_mm","sep":"","prefix":"厚"},{"key":"width","sep":"*","prefix":"宽"}],"with_unit":true,"unit_position":"each"}}
                """, json);
            jdbc.update("INSERT INTO inventory_attribute_template (template_id,template_code,template_name,category_id,attribute_schema) VALUES (9,'TEST_RULE','测试规则',26,?)", json.writeValueAsString(schema));
            for (long id=1; id<=3; id++) jdbc.update("INSERT INTO inventory_material(material_id,material_code,material_name,model,category_id,specification,width_mm,thickness_mm,unit) VALUES (?,?,?, ?,26,'2*365mm',365,2,'M')", id,"TEST"+id,"旧名"+id,"M"+id);
            var mybatis = new MybatisConfiguration(); mybatis.setMapUnderscoreToCamelCase(true);
            mybatis.addMapper(InventoryMaterialMapper.class); mybatis.addMapper(MaterialMasterMapper.class); mybatis.addMapper(SysOperLogMapper.class);
            var bean = new MybatisSqlSessionFactoryBean(); bean.setDataSource(source); bean.setConfiguration(mybatis);
            bean.setGlobalConfig(new GlobalConfig().setBanner(false));
            var session = new SqlSessionTemplate(bean.getObject());
            var materials = session.getMapper(InventoryMaterialMapper.class); var locks = session.getMapper(MaterialMasterMapper.class);
            var catalog = mock(MaterialMasterCatalog.class);
            var category = new MaterialMasterCatalog.Category(26L,"FOAM","泡棉",null,3,"R","泡棉",true,0,1,"FOAM");
            var config = new MaterialMasterCatalog.Configuration(category,List.of(category),locks.lockTemplate(26L),schema);
            when(catalog.optionalConfiguration(26L)).thenReturn(config); when(catalog.specificationSchema(26L)).thenReturn(schema);
            var master = new MaterialMasterService(catalog,locks,materials,null,null,null,mock(EventPublisher.class),json);
            var logs = session.getMapper(SysOperLogMapper.class);
            var manager = new DataSourceTransactionManager(source);
            var application = new MaterialRuleApplicationService(master,catalog,locks,materials,logs,manager,json);
            var options = new MaterialRuleApplicationService.Options(26L,true,true,false);
            var preview = application.preview(options,1,20,null);
            assertEquals(3,preview.total()); assertEquals("厚2mm*宽365mm",preview.records().get(0).newSpecification());
            var selected = preview.records().get(1);
            var result = application.apply(new MaterialRuleApplicationService.ApplyRequest(options,List.of(
                    new MaterialRuleApplicationService.Selection(selected.materialId(),selected.previewToken()))),"codex",1L,"测试用户");
            assertEquals(1,result.success());
            assertEquals("新名-M2",jdbc.queryForObject("SELECT material_name FROM inventory_material WHERE material_id=2",String.class));
            assertEquals("旧名1",jdbc.queryForObject("SELECT material_name FROM inventory_material WHERE material_id=1",String.class));
            assertEquals("M2",jdbc.queryForObject("SELECT model FROM inventory_material WHERE material_id=2",String.class));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM sys_oper_log WHERE biz_id='2' AND detail LIKE '%旧名2%'",Integer.class));
            var brokenLogs = mock(SysOperLogMapper.class);
            when(brokenLogs.insert(any(com.jjx.system.domain.entity.SysOperLog.class))).thenReturn(0);
            var failing = new MaterialRuleApplicationService(master,catalog,locks,materials,brokenLogs,manager,json);
            selected = failing.preview(options,1,20,null).records().get(0);
            var failed = failing.apply(new MaterialRuleApplicationService.ApplyRequest(options,List.of(
                    new MaterialRuleApplicationService.Selection(selected.materialId(),selected.previewToken()))),"codex",1L,"测试用户");
            assertEquals(1,failed.failed());
            assertEquals("旧名1",jdbc.queryForObject("SELECT material_name FROM inventory_material WHERE material_id=1",String.class));
            assertEquals("2*365mm",jdbc.queryForObject("SELECT specification FROM inventory_material WHERE material_id=1",String.class));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM sys_oper_log",Integer.class));
        } finally { source.destroy(); }
    }
}
