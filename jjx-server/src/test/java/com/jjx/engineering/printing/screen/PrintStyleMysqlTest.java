package com.jjx.engineering.printing.screen;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.engineering.printing.artwork.PrintStyleService;
import com.jjx.system.utils.SecurityUtils;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 每轮生成全新隔离schema；仅从本机开发库复制表结构，绝不写开发库业务行。 */
@EnabledIfEnvironmentVariable(named="JJX_PRINT_STYLE_MYSQL",matches="1")
class PrintStyleMysqlTest {
    static String schema;
    static JdbcTemplate admin;
    JdbcTemplate db;
    ScreenService screens;
    PrintStyleService styles;
    TransactionTemplate tx;
    ObjectMapper json=new ObjectMapper();
    static final List<String> TABLES=List.of("print_artwork","print_artwork_version","print_film_copy",
        "engineering_screen_frame","print_frame_stretch","engineering_screen_plate","engineering_resource_maintenance",
        "sys_number_sequence","engineering_resource_product_rel","product","print_use_segment","sales_sample_process");
    @BeforeAll static void database() {
        schema="jjx_print008_test_"+UUID.randomUUID().toString().replace("-","");
        admin=new JdbcTemplate(new DriverManagerDataSource("jdbc:mysql://127.0.0.1:3306/?useSSL=false&allowPublicKeyRetrieval=true", "root", System.getenv("DB_PASS")));
        admin.execute("CREATE DATABASE "+schema+" CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        for(String table:TABLES) admin.execute("CREATE TABLE "+schema+"."+table+" LIKE jjx_erp_db."+table);
    }
    @AfterAll static void cleanup() {
        if(admin!=null && schema!=null && schema.matches("jjx_print008_test_[0-9a-f]{32}")) admin.execute("DROP DATABASE "+schema);
    }
    @BeforeEach void setup() {
        var source=new DriverManagerDataSource("jdbc:mysql://127.0.0.1:3306/"+schema+"?useSSL=false&allowPublicKeyRetrieval=true","root",System.getenv("DB_PASS"));
        db=new JdbcTemplate(source);tx=new TransactionTemplate(new DataSourceTransactionManager(source));
        for(String table:TABLES) db.update("DELETE FROM "+table);
        styles=new PrintStyleService(db,json);screens=new ScreenService(new ScreenRepository(db),json);
        ReflectionTestUtils.setField(screens,"styles",styles);
        db.update("INSERT INTO print_artwork(artwork_id,artwork_no,name,family,number_series) VALUES(1,'YL1025','测试','面板','YL1025'),(2,'OTHER','测试二','面板','GY')");
        db.update("INSERT INTO print_artwork_version(version_id,artwork_id,version_no,status,styles_json) VALUES(11,1,'V1','RELEASED',?),(12,2,'V2','RELEASED',?)",
            "[{\"styleNo\":\"A\",\"name\":\"样式A\",\"inkColor\":\"RED\"},{\"styleNo\":\"B\",\"name\":\"样式B\",\"inkColor\":\"BLUE\"}]",
            "[{\"styleNo\":\"C\",\"name\":\"样式C\",\"inkColor\":\"BLACK\"}]");
        db.update("INSERT INTO print_film_copy(film_id,film_no,version_id,status,thickness_mm) VALUES(21,'FL-A',11,'AVAILABLE',0.1),(22,'FL-B',12,'AVAILABLE',0.1)");
        db.update("INSERT INTO engineering_screen_frame(frame_id,frame_no,status,v2_registered) VALUES(1,'FRAME-1','READY',1)");
        db.update("INSERT INTO print_frame_stretch(stretch_id,frame_id,mesh,tension_n_cm,qualified,stretch_time) VALUES(1,1,200,20,1,NOW())");
    }
    ScreenCommands command(int revision,Map<String,Object> fields) {
        var body=new HashMap<>(fields);body.put("requestId",UUID.randomUUID().toString());body.put("expectedRevision",revision);
        return json.convertValue(body,ScreenCommands.class);
    }
    Map<String,Object> execute(String user,long id,String action,ScreenCommands command) {
        try(var security=mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUsername).thenReturn(user);
            return tx.execute(status->screens.execute(false,id,action,command));
        }
    }
    ScreenCommands creation(String raw) {return command(0,Map.of("frameId",1,"stretchId",1,"plateNo","PLATE-1","position","面板","sourcesJson",raw,"layout","A左C右"));}
    static final String SOURCES="[{\"filmId\":21,\"versionId\":11,\"styleNos\":[\"A\"]},{\"filmId\":22,\"versionId\":12,\"styleNos\":[\"C\"]}]";
    long released() {
        var cmd=creation(SOURCES);var result=execute("maker",0,"create",cmd);execute("maker",0,"create",cmd);
        long id=((Number)result.get("plateId")).longValue();
        execute("inspector",id,"release",command(0,Map.of("inspection","检验合格")));return id;
    }
    @Test void multiFilmPartialStylesReplayAndIndependentInk() throws Exception {
        long id=released();
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM engineering_screen_plate",Integer.class));
        var source=json.readTree(db.queryForObject("SELECT sources_json FROM engineering_screen_plate",String.class));
        assertEquals(2,source.size());assertEquals(1,source.get(0).path("styles").size());
        assertEquals("A左C右",db.queryForObject("SELECT layout FROM engineering_screen_plate",String.class));
        String raw="{\"styles\":[{\"versionId\":11,\"styleNo\":\"A\",\"inkColor\":\"OVERRIDE\"},{\"versionId\":12,\"styleNo\":\"C\"}]}";
        String normalized=tx.execute(status->screens.normalizeStyleRequirements(id,raw));
        assertEquals("OVERRIDE",json.readTree(normalized).path("styles").get(0).path("inkColor").asText());
        assertEquals("BLACK",json.readTree(normalized).path("styles").get(1).path("inkColor").asText());
        tx.executeWithoutResult(status->screens.requireUsablePlate(12,id,normalized));
        assertEquals(1,screens.countUsablePlate(12,normalized));
    }
    @Test void mismatchedVersionAndMissingStylesRollbackBeforeOccupation() {
        assertThrows(BusinessException.class,()->execute("maker",0,"create",creation(SOURCES.replace("\"filmId\":22","\"filmId\":21"))));
        assertThrows(BusinessException.class,()->execute("maker",0,"create",creation(SOURCES.replace("\"A\"","\"C\""))));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM engineering_screen_plate",Integer.class));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM engineering_resource_maintenance",Integer.class));
        assertEquals(0,db.queryForObject("SELECT revision FROM engineering_screen_frame",Integer.class));
        long id=released();
        assertThrows(BusinessException.class,()->tx.executeWithoutResult(status->screens.requireUsablePlate(11,id,"{}")));
        assertThrows(BusinessException.class,()->tx.executeWithoutResult(status->screens.requireUsablePlate(11,id,"{\"styles\":[{\"versionId\":11,\"styleNo\":\"B\"}]}")));
        db.update("UPDATE print_artwork_version SET status='DISABLED' WHERE version_id=12");
        assertEquals(0,screens.countUsablePlate(11,"{\"styles\":[{\"versionId\":11,\"styleNo\":\"A\"}]}"));
    }
    @Test void persistentSequenceSerializesConcurrentBatchesAndRollsBack() throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(4);
        try {
            List<Callable<String>> jobs=new ArrayList<>();
            for(int i=0;i<16;i++) jobs.add(()->tx.execute(status->styles.next("FL","YL1025")));
            Set<String> values=new HashSet<>();for(var future:pool.invokeAll(jobs)) values.add(future.get(10,TimeUnit.SECONDS));
            assertEquals(16,values.size());assertTrue(values.contains("YL1025-FL-001"));assertTrue(values.contains("YL1025-FL-016"));
            tx.executeWithoutResult(status->{styles.next("FL","YL1025");status.setRollbackOnly();});
            assertEquals("YL1025-FL-017",tx.execute(status->styles.next("FL","YL1025")));
        } finally {pool.shutdown();}
    }
    @Test void styleCodesStayStableWhenArtworkBecomesShared() {
        assertEquals("YL1025",PrintStyleService.series(List.of("YL1025PWOO","YL1025SX")));
        assertEquals("GY",PrintStyleService.series(List.of("YL1025PWOO","YL1080PWOO")));
        assertEquals("旧框1-BW-001",tx.execute(status->styles.next("BW","旧框1")));
        var first=tx.execute(status->styles.saveStyles(1,"[{\"name\":\"正面\",\"inkColor\":\"RED\"}]",null));
        assertEquals("YL1025-YS-001",first.get(0).path("styleNo").asText());
        var saved=tx.execute(status->styles.saveStyles(1,first.toString(),first.toString()));
        assertEquals(first,saved);
        assertThrows(BusinessException.class,()->tx.execute(status->styles.saveStyles(2,first.toString(),null)));
    }

    @Test void usableCountExcludesWrongParametersMaintenanceAndDisabledVersion() {
        long id=released();
        String required="{\"styles\":[{\"versionId\":11,\"styleNo\":\"A\"}],\"meshMin\":100,\"position\":\"面板\"}";
        assertEquals(1,screens.countUsablePlate(11,required));
        assertEquals(0,screens.countUsablePlate(11,required.replace("100","201")));
        assertEquals(0,screens.countUsablePlate(11,required.replace("面板","下线")));
        db.update("UPDATE engineering_screen_frame SET status='MAINTENANCE'");
        assertEquals(0,screens.countUsablePlate(11,required));
        db.update("UPDATE engineering_screen_frame SET status='READY'");
        db.update("UPDATE print_artwork_version SET status='DISABLED' WHERE version_id=12");
        assertEquals(0,screens.countUsablePlate(11,required));
    }

    @Test void physicalOccupationIsSharedAcrossSampleAndProduction() {
        long id=released();
        db.update("INSERT INTO sales_sample_process(process_id,order_id,process_name,actual_plate_id,status) VALUES(91,1,'印刷',?,?)",
            id,com.jjx.sales.enums.SampleProcessStatusEnum.IN_PROGRESS.getValue());
        assertThrows(BusinessException.class,()->tx.executeWithoutResult(status->screens.requireAvailableForUse(id,1L,null)));
        assertThrows(BusinessException.class,()->tx.executeWithoutResult(status->screens.requireAvailableForUse(id,null,92L)));
        assertDoesNotThrow(()->tx.executeWithoutResult(status->screens.requireAvailableForUse(id,null,91L)));
    }
}
