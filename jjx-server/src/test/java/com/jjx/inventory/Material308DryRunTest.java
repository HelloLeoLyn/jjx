package com.jjx.inventory;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.event.EventPublisher;
import com.jjx.inventory.classify.*;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.mapper.*;
import com.jjx.inventory.master.*;
import com.jjx.inventory.service.InventoryMaterialService;
import com.jjx.purchase.mapper.PurchaseSupplierMapper;
import com.jjx.system.mapper.*;
import com.jjx.system.service.ISysTagService;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import java.io.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 仅连接用户指定的308演练库；真实Mapper/目录/模板，写入均在外层事务内回滚，不启服务。 */
@EnabledIfEnvironmentVariable(named = "JJX_TEST_308", matches = "1")
class Material308DryRunTest {
    private JdbcTemplate jdbc;
    private DataSourceTransactionManager manager;
    private TransactionStatus transaction;
    private InventoryMaterialMapper materials;
    private MaterialMasterMapper locks;
    private MaterialMasterCatalog catalog;
    private MaterialClassifyService classify;
    private MaterialMasterExcelService excel;
    private MaterialMasterService master;
    private ObjectMapper json;
    private Long pcId;
    private MaterialClassifyRuleMapper rules;
    private SqlSessionTemplate session;
    @BeforeEach void setup() throws Exception {
        var source=new DriverManagerDataSource("jdbc:mysql://127.0.0.1:3306/jjx_dryrun_308?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",
                System.getenv().getOrDefault("DB_USER","root"),System.getenv().getOrDefault("DB_PASS","123456"));
        jdbc=new JdbcTemplate(source); manager=new DataSourceTransactionManager(source);
        assertEquals("jjx_dryrun_308",jdbc.queryForObject("SELECT DATABASE()",String.class));
        MybatisConfiguration config=new MybatisConfiguration();config.setMapUnderscoreToCamelCase(true);
        for (var type:List.of(InventoryMaterialMapper.class,InventoryMaterialCategoryMapper.class,AttributeTemplateMapper.class,
                MaterialMasterMapper.class,MaterialClassifyRuleMapper.class,SysDictMapper.class,SysDictItemMapper.class)) config.addMapper(type);
        var bean=new MybatisSqlSessionFactoryBean();bean.setDataSource(source);bean.setConfiguration(config);bean.setGlobalConfig(new GlobalConfig().setBanner(false));
        session=new SqlSessionTemplate(bean.getObject());
        materials=session.getMapper(InventoryMaterialMapper.class);locks=session.getMapper(MaterialMasterMapper.class);rules=session.getMapper(MaterialClassifyRuleMapper.class);
        json=new ObjectMapper();catalog=new MaterialMasterCatalog(session.getMapper(InventoryMaterialCategoryMapper.class),session.getMapper(AttributeTemplateMapper.class),
                session.getMapper(SysDictItemMapper.class),session.getMapper(SysDictMapper.class),json);
        classify=proxy(new MaterialClassifyService(rules,materials,locks,catalog,json));
        var numbers=mock(InventoryMaterialService.class);
        when(numbers.generateCode(anyLong())).thenAnswer(i->"T308"+UUID.randomUUID().toString().replace("-",""));
        when(numbers.generateUnclassifiedCode()).thenAnswer(i->"T308U"+UUID.randomUUID().toString().replace("-",""));
        var tags=mock(ISysTagService.class);when(tags.getBizTags(anyString(),anyLong())).thenReturn(List.of());
        master=proxy(new MaterialMasterService(catalog,locks,materials,numbers,mock(PurchaseSupplierMapper.class),tags,mock(EventPublisher.class),json));
        excel=proxy(new MaterialMasterExcelService(catalog,master,materials,json,classify));
        pcId=catalog.categories().stream().filter(c->c.categoryCode().equals("RAW_BASE_PC")).findFirst().orElseThrow().categoryId();
        transaction=manager.getTransaction(new DefaultTransactionDefinition());
    }
    @SuppressWarnings("unchecked") private <T> T proxy(T target) {
        var factory=new ProxyFactory(target);factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));return (T) factory.getProxy();
    }
    @AfterEach void rollback() {
        if (transaction!=null && !transaction.isCompleted()) manager.rollback(transaction);
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM inventory_material WHERE material_code LIKE 'T308%'",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM inventory_material_classify_rule",Integer.class));
    }
    private InventoryMaterial pending(String name) {
        var material=new InventoryMaterial();material.setMaterialCode("T308"+UUID.randomUUID().toString().replace("-",""));
        material.setMaterialName(name);material.setUnit("PCS");material.setAttributeJson("{\"material\":\"PC\"}");
        material.setCreateBy("codex-308-test");material.setCreateTime(java.time.LocalDateTime.now());material.setUpdateBy("codex-308-test");material.setUpdateTime(java.time.LocalDateTime.now());
        materials.insert(material);return material;
    }
    private MaterialClassifyRule rule(String keyword, String category, MaterialClassifyRule.MatchField field, int priority) {
        var rule=new MaterialClassifyRule();rule.setKeyword(keyword);rule.setCategoryCode(category);rule.setMatchField(field);rule.setPriority(priority);rule.setEnabled(true);
        return classify.saveRule(rule,false,"codex-308-test");
    }
    private int count(String state) {
        var wrapper=new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<InventoryMaterial>();MaterialClassStatus.filter(wrapper,state);
        return Math.toIntExact(materials.selectCount(wrapper));
    }
    private void requireThickness() throws Exception {
        Long template=catalog.configuration(pcId).template().getTemplateId();
        var schema=json.readTree(catalog.configuration(pcId).template().getAttributeSchema());
        for (var field:schema.get("fields")) if(field.get("key").asText().equals("thickness_mm")) ((com.fasterxml.jackson.databind.node.ObjectNode)field).put("required",true);
        jdbc.update("UPDATE inventory_attribute_template SET attribute_schema=? WHERE template_id=?",json.writeValueAsString(schema),template);
        session.clearCache();
    }
    private static MockMultipartFile file(String[] headers,String[]... rows) throws Exception {
        try(var workbook=new XSSFWorkbook();var out=new ByteArrayOutputStream()) {
            var sheet=workbook.createSheet();var header=sheet.createRow(0);
            for(int i=0;i<headers.length;i++) header.createCell(i).setCellValue(headers[i]);
            for(int r=0;r<rows.length;r++){var row=sheet.createRow(r+1);for(int c=0;c<rows[r].length;c++)row.createCell(c).setCellValue(rows[r][c]);}
            workbook.write(out);return new MockMultipartFile("file","308.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",out.toByteArray());
        }
    }
    @Test void migrationBackfillAndThreeStatusFiltersAreExact() {
        assertEquals(50,jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_material'",Integer.class));
        assertEquals(566,count("unclassified"));assertEquals(1041,count("rule_unconfirmed"));assertEquals(0,count("confirmed"));
        assertEquals("utf8mb4_unicode_ci",jdbc.queryForObject("SELECT TABLE_COLLATION FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='inventory_material_classify_rule'",String.class));
    }
    @Test void ruleCrudUsesUniquePairAndDeterministicPriority() {
        var first=rule("保护膜","RAW_BASE_PC",MaterialClassifyRule.MatchField.ANY,1);
        var protect=catalog.categories().stream().filter(c->c.selectable() && c.categoryName().contains("保护膜")).findFirst().orElseThrow();
        var specific=rule("保护膜",protect.categoryCode(),MaterialClassifyRule.MatchField.NAME,100);
        assertEquals(specific.getId(),classify.match("pet保护膜","PC").ruleId());
        assertThrows(BusinessException.class,()->classify.saveRule(first,false,"codex")); // 新增不得指定ID。
        specific.setEnabled(false);classify.saveRule(specific,true,"codex-308-test");
        assertEquals(first.getId(),classify.match("pet保护膜","").ruleId());
        classify.deleteRule(first.getId());assertNull(classify.match("保护膜","PC"));
        assertEquals("codex-308-test",classify.listRules().get(0).getUpdateBy());
    }
    @Test void rulePreviewIsReadOnlyAndApplyOnlyTouchesUnclassifiedThenConfirmClosesLoop() {
        var pending=pending("T308专属规则PC");rule("T308专属规则","RAW_BASE_PC",MaterialClassifyRule.MatchField.NAME,1);
        var preview=classify.applyRules(false,"codex");assertTrue(preview.dryRun());assertEquals(1,preview.changes().size());
        assertNull(materials.selectById(pending.getMaterialId()).getCategoryId());
        assertEquals(1,classify.applyRules(true,"codex").changes().size());
        var actual=materials.selectById(pending.getMaterialId());assertEquals("RULE",actual.getClassSource());assertFalse(actual.getClassConfirmed());
        assertEquals(pcId,actual.getCategoryId());assertEquals(catalog.configuration(pcId).template().getTemplateId(),actual.getTemplateId());
        classify.confirm(pending.getMaterialId(),"codex");assertTrue(materials.selectById(pending.getMaterialId()).getClassConfirmed());
        assertEquals(1,count("confirmed"));assertTrue(classify.applyRules(true,"codex").changes().isEmpty());
    }
    @Test void batchClassifies200Rejects201AndReturnsFailures() {
        var ids=new ArrayList<Long>();for(int i=0;i<200;i++)ids.add(pending("批量"+i).getMaterialId());
        var result=classify.classify(new MaterialClassifyService.BatchRequest(ids,pcId),"codex");
        assertEquals(200,result.successCount());assertEquals(0,result.failCount());assertEquals(200,count("confirmed"));
        for(Long id:ids){var material=materials.selectById(id);assertEquals("MANUAL",material.getClassSource());assertEquals(pcId,material.getCategoryId());}
        ids.add(99999999L);assertThrows(BusinessException.class,()->classify.classify(new MaterialClassifyService.BatchRequest(ids,pcId),"codex"));
    }
    @Test void batchUnexpectedDatabaseFailureMarksWholeTransactionForRollback() {
        var one=pending("回滚1");var two=pending("回滚2");var failing=spy(materials);
        int[] writes={0};doAnswer(call->{if(++writes[0]==2)throw new org.springframework.dao.DataAccessResourceFailureException("模拟第二条DB失败");return materials.update(null,call.getArgument(1));})
                .when(failing).update(isNull(),any(com.baomidou.mybatisplus.core.conditions.Wrapper.class));
        var service=proxy(new MaterialClassifyService(rules,failing,locks,catalog,json));
        assertThrows(org.springframework.dao.DataAccessResourceFailureException.class,()->service.classify(new MaterialClassifyService.BatchRequest(List.of(one.getMaterialId(),two.getMaterialId()),pcId),"codex"));
        assertTrue(transaction.isRollbackOnly());
        manager.rollback(transaction);transaction=null;
        assertNull(materials.selectById(one.getMaterialId()));assertNull(materials.selectById(two.getMaterialId()));
    }
    @Test void unifiedTemplateHasCategoryAndUnionFieldsAndImportValidatesEachRowWithoutPartialWrites() throws Exception {
        try(var workbook=new XSSFWorkbook(new ByteArrayInputStream(excel.template(null)))) {
            var header=workbook.getSheetAt(0).getRow(1);assertEquals("品类",header.getCell(0).getStringCellValue());
            assertTrue(java.util.stream.StreamSupport.stream(header.spliterator(),false).anyMatch(c->c.getStringCellValue().equals("厚度(mm)")));
        }
        requireThickness();
        var missing=excel.importExcel(file(new String[]{"品类","原名称","原规格"},new String[]{"RAW_BASE_PC","缺属性PC","源规格"}),null,false,"codex",false,false);
        assertEquals(0,missing.successCount());assertTrue(missing.errors().get(0).contains("第2行：缺【厚度(mm)】列"));
        var bad=excel.importExcel(file(new String[]{"品类","原名称","材质","厚度(mm)"},new String[]{"RAW_BASE_PC","越界PC","PET","0.175"},new String[]{"不存在","错误品类","PC","0.175"}),null,false,"codex",false,false);
        assertEquals(2,bad.errors().size());assertTrue(bad.errors().get(0).contains("材质】越界"));assertTrue(bad.errors().get(1).contains("品类】不存在"));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM inventory_material WHERE material_code LIKE 'T308%'",Integer.class));
    }
    @Test void oldNameSpecOnlyImportsAndAutoClassifyFallsBackToUnclassified() throws Exception {
        var legacy=file(new String[]{"名称","规格"},new String[]{"308旧模板无法识别","原始规格"});
        assertEquals(1,excel.importExcel(legacy,null,true,"codex",false,false).successCount());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM inventory_material WHERE material_code LIKE 'T308%'",Integer.class));
        assertEquals(1,excel.importExcel(legacy,null,false,"codex",false,true).successCount());
        var material=jdbc.queryForMap("SELECT category_id,class_source,class_confirmed,material_name,specification FROM inventory_material WHERE material_code LIKE 'T308%'");
        assertNull(material.get("category_id"));assertNull(material.get("class_source"));assertEquals(false,material.get("class_confirmed"));
        assertEquals("308旧模板无法识别",material.get("material_name"));assertNull(material.get("specification"));
    }
    @Test void autoRuleAndExplicitCategoryImportsWriteDistinctSources() throws Exception {
        rule("308规则PC","RAW_BASE_PC",MaterialClassifyRule.MatchField.NAME,1);
        var file=file(new String[]{"品类","原名称","厚度(mm)"},new String[]{"","308规则PC","0.175"},new String[]{"RAW_BASE_PC","308指定PC","0.175"});
        var result=excel.importExcel(file,null,false,"codex",false,true);assertTrue(result.errors().isEmpty(),result.errors().toString());assertEquals(2,result.successCount());
        assertEquals("RULE",jdbc.queryForObject("SELECT class_source FROM inventory_material WHERE material_name='308规则PC'",String.class));
        assertEquals("IMPORT",jdbc.queryForObject("SELECT class_source FROM inventory_material WHERE material_name='308指定PC'",String.class));
        assertEquals(1,count("confirmed"));
    }
    @Test void confirmationRequiresTemplateFieldsAndUnknownAttributeKeysAreRejected() throws Exception {
        requireThickness();var material=pending("未补厚度PC");
        var result=classify.classify(new MaterialClassifyService.BatchRequest(List.of(material.getMaterialId(),99999999L),pcId),"codex");
        assertEquals(0,result.successCount());assertEquals(2,result.failCount());assertTrue(result.details().get(0).message().contains("厚度"));
        assertNull(materials.selectById(material.getMaterialId()).getCategoryId());
    }
    @Test void legacyCategoryMetadataFileStillImportsThroughNewValidator() throws Exception {
        MockMultipartFile file;
        try(var workbook=new XSSFWorkbook();var out=new ByteArrayOutputStream()) {
            var sheet=workbook.createSheet();var metadata=sheet.createRow(0);
            metadata.createCell(0).setCellValue("品类编码");metadata.createCell(1).setCellValue("RAW_BASE_PC");
            metadata.createCell(2).setCellValue("模板版本");metadata.createCell(3).setCellValue(String.valueOf(catalog.configuration(pcId).schema().version()));
            var headers=sheet.createRow(1);headers.createCell(0).setCellValue("原名称");headers.createCell(1).setCellValue("原规格");headers.createCell(2).setCellValue("厚度(mm)");
            var row=sheet.createRow(2);row.createCell(0).setCellValue("308旧品类模板");row.createCell(1).setCellValue("来源");row.createCell(2).setCellValue("0.175");
            workbook.write(out);file=new MockMultipartFile("file","old.xlsx","application/octet-stream",out.toByteArray());
        }
        var result=excel.importExcel(file,null,false,"codex",false,false);
        assertTrue(result.errors().isEmpty(),result.errors().toString());assertEquals(1,result.successCount());
    }
    @Test void ruleUniquePairIsEnforcedByRealDatabase() {
        rule("308唯一关键词","RAW_BASE_PC",MaterialClassifyRule.MatchField.NAME,0);
        var duplicate=new MaterialClassifyRule();duplicate.setKeyword("308唯一关键词");duplicate.setCategoryCode("RAW_BASE_PC");duplicate.setMatchField(MaterialClassifyRule.MatchField.SPEC);
        assertTrue(assertThrows(BusinessException.class,()->classify.saveRule(duplicate,false,"codex")).getMessage().contains("已存在"));
        assertEquals(1,classify.listRules().size());
    }
    @Test void classifiedImportRequiresFieldsAndCannotOverwriteManualConfirmation() throws Exception {
        var dto=new MaterialMasterDTO();dto.setCategoryId(pcId);dto.setMaterialName("308人工确认PC");dto.setAttributeJson(Map.of("thickness_mm",0.175));
        var confirmed=master.save(dto,"codex");assertTrue(confirmed.getClassConfirmed());assertEquals("MANUAL",confirmed.getClassSource());
        var result=excel.importExcel(file(new String[]{"品类","原名称","物料ID（更新时填写）"},new String[]{"RAW_BASE_PC","308企图覆盖",confirmed.getMaterialId().toString()}),null,false,"codex",true,true);
        assertEquals(0,result.successCount());assertTrue(result.errors().get(0).contains("不能覆盖"));
        assertEquals("308人工确认PC",materials.selectById(confirmed.getMaterialId()).getMaterialName());
    }

}
