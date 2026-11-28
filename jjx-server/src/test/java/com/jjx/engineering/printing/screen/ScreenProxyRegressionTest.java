package com.jjx.engineering.printing.screen;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.system.utils.SecurityUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.annotation.PersistenceExceptionTranslationPostProcessor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

/** 实际Spring仓储异常转换代理与事务，SQL只在独立H2内执行，不连接业务库。 */
class ScreenProxyRegressionTest {
    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {
        @Bean static PersistenceExceptionTranslationPostProcessor repositoryProxy() {
            var processor = new PersistenceExceptionTranslationPostProcessor();
            processor.setProxyTargetClass(true);
            return processor;
        }
        @Bean DataSource source() {
            // 保留MySQL返回的驼峰列别名，重放JSON不能依赖H2的大小写不敏感Map。
            return new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID()
                    + ";MODE=MySQL;DATABASE_TO_UPPER=FALSE;DB_CLOSE_DELAY=-1", "sa", "");
        }
        @Bean JdbcTemplate jdbc(DataSource source) {
            return new JdbcTemplate(source) {
                @Override public int update(String sql, Object... args) {
                    // H2不支持MySQL IF表达式，仅在测试适配成等价CASE；仍执行真实SQL与事务。
                    return super.update(sql.replace("washed_time=if(?='wash',now(),null)",
                            "washed_time=CASE WHEN ?='wash' THEN now() ELSE NULL END"), args);
                }
            };
        }
        @Bean PlatformTransactionManager transactions(DataSource source) { return new DataSourceTransactionManager(source); }
        @Bean ObjectMapper json() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean ScreenRepository repository(JdbcTemplate jdbc) { return new ScreenRepository(jdbc); }
        @Bean ScreenService service(ScreenRepository repository, ObjectMapper json) { return new ScreenService(repository, json); }
        @Bean ScreenView view(ScreenRepository repository) { return new ScreenView(repository); }
    }

    AnnotationConfigApplicationContext context;
    JdbcTemplate db;
    ObjectMapper json;
    ScreenService service;
    ScreenView view;

    @BeforeEach void setup() {
        context = new AnnotationConfigApplicationContext(Config.class);
        db = context.getBean(JdbcTemplate.class);
        json = context.getBean(ObjectMapper.class);
        service = context.getBean(ScreenService.class);
        view = context.getBean(ScreenView.class);
        db.execute("CREATE TABLE engineering_screen_frame(frame_id BIGINT AUTO_INCREMENT PRIMARY KEY,frame_no VARCHAR(50) UNIQUE,frame_type VARCHAR(30),location VARCHAR(200),status VARCHAR(20),revision INT DEFAULT 0,remark VARCHAR(500),v2_registered INT,del_flag CHAR DEFAULT '0',create_by VARCHAR(64),update_by VARCHAR(64))");
        db.execute("CREATE TABLE engineering_resource_maintenance(maintenance_id BIGINT AUTO_INCREMENT PRIMARY KEY,resource_type VARCHAR(30),resource_id BIGINT,action_type VARCHAR(30),operator VARCHAR(64),request_id VARCHAR(64) UNIQUE,request_hash VARCHAR(64),result_snapshot VARCHAR(16000),before_status VARCHAR(20),after_status VARCHAR(20),description VARCHAR(500),operate_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        db.execute("CREATE TABLE print_frame_stretch(stretch_id BIGINT AUTO_INCREMENT PRIMARY KEY,frame_id BIGINT,mesh INT,tension_n_cm DECIMAL(10,2),qualified INT,end_time TIMESTAMP,material VARCHAR(200),stretch_time TIMESTAMP,create_by VARCHAR(64),open_frame_id BIGINT GENERATED ALWAYS AS (CASE WHEN end_time IS NULL THEN frame_id ELSE NULL END),UNIQUE(open_frame_id))");
        db.execute("CREATE TABLE engineering_screen_plate(plate_id BIGINT AUTO_INCREMENT PRIMARY KEY,frame_id BIGINT,plate_no VARCHAR(80) UNIQUE,version_id BIGINT,film_copy_id BIGINT,stretch_id BIGINT,position VARCHAR(100),status VARCHAR(20),v2_registered INT,revision INT DEFAULT 0,create_by VARCHAR(64),update_by VARCHAR(64),released_by VARCHAR(64),released_time TIMESTAMP,washed_time TIMESTAMP,end_reason VARCHAR(500),occupied_frame_id BIGINT GENERATED ALWAYS AS (CASE WHEN status IN ('PENDING','FAILED','RELEASED') THEN frame_id ELSE NULL END),UNIQUE(occupied_frame_id))");
        db.execute("CREATE TABLE print_artwork_version(version_id BIGINT PRIMARY KEY,status VARCHAR(20))");
        db.execute("CREATE TABLE print_film_copy(film_id BIGINT PRIMARY KEY,version_id BIGINT,status VARCHAR(20))");
        db.execute("CREATE TABLE print_use_segment(segment_id BIGINT AUTO_INCREMENT PRIMARY KEY,open_plate_id BIGINT)");
        db.execute("CREATE TABLE sales_sample_process(process_id BIGINT PRIMARY KEY,actual_plate_id BIGINT,status INT)");
        db.update("INSERT INTO print_artwork_version VALUES (11,'RELEASED'),(12,'DRAFT')");
        db.update("INSERT INTO print_film_copy VALUES (21,11,'AVAILABLE'),(22,12,'AVAILABLE')");
    }

    @AfterEach void close() { if (context != null) context.close(); }

    ScreenCommands command(int revision, Map<String, Object> fields) {
        var values = new HashMap<>(fields);
        values.put("requestId", UUID.randomUUID().toString());
        values.put("expectedRevision", revision);
        return json.convertValue(values, ScreenCommands.class);
    }

    Map<String, Object> run(String user, boolean frame, long id, String action, ScreenCommands command) {
        try (var security = mockStatic(SecurityUtils.class)) {
            security.when(SecurityUtils::getUsername).thenReturn(user);
            return service.execute(frame, id, action, command);
        }
    }

    @Test void repositoryProxyFieldIsEmptyButItsTargetHasInjectedJdbcTemplate() throws Exception {
        var repository = context.getBean(ScreenRepository.class);
        assertTrue(AopUtils.isCglibProxy(repository));
        assertTrue(AopUtils.isAopProxy(service));
        assertTrue(AopUtils.isAopProxy(view));
        var field = ScreenRepository.class.getDeclaredField("db");
        field.setAccessible(true);
        assertNull(field.get(repository), "直接读取代理字段无法取得目标对象注入的依赖");
        assertSame(db, field.get(((Advised) repository).getTargetSource().getTarget()));
    }

    @Test void createStretchPlateReleaseAndWashWorkThroughSpringProxies() {
        var create = command(0, Map.of("frameNo", "WK-001", "location", "A架"));
        var created = run("maker", true, 0, "create", create);
        long frameId = ((Number) created.get("frameId")).longValue();
        assertEquals(frameId, ((Number) run("maker", true, 0, "create", create).get("frameId")).longValue());
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM engineering_screen_frame", Integer.class));
        var stretched = run("maker", true, frameId, "stretch",
                command(0, Map.of("mesh", 300, "tensionNcm", 20, "qualified", true)));
        long stretchId = ((Number) stretched.get("stretchId")).longValue();
        var frameView = view.get(true, frameId);
        assertEquals(stretchId, frameView.get("stretchId"));
        assertEquals(1, ((java.util.List<?>) frameView.get("stretches")).size());
        var plated = run("maker", false, 0, "create", command(1, Map.of("frameId", frameId,
                "stretchId", stretchId, "versionId", 11, "filmId", 21, "plateNo", "PL-001", "position", "正面")));
        long plateId = ((Number) plated.get("plateId")).longValue();
        assertEquals("PENDING", view.get(false, plateId).get("status"));
        var inspect = command(0, Map.of("inspection", "对位及图案检验合格"));
        assertEquals(403, assertThrows(BusinessException.class,
                () -> run("maker", false, plateId, "release", inspect)).getCode());
        run("inspector", false, plateId, "release", inspect);
        assertEquals("RELEASED", view.get(false, plateId).get("status"));
        assertEquals(1, service.countUsablePlate(11, "{\"meshMin\":300,\"position\":\"正面\"}"));
        run("maker", false, plateId, "wash", command(1, Map.of("reason", "换图案")));
        assertEquals("WASHED", view.get(false, plateId).get("status"));
        assertNull(view.get(true, frameId).get("plateId"));
        assertEquals(0, service.countUsablePlate(11));
        assertEquals(3, ((java.util.List<?>) view.get(false, plateId).get("history")).size());
    }

    @Test void invalidWriteRollsBackAuditAndDuplicateFrameKeepsOriginal() {
        assertThrows(BusinessException.class, () -> run("maker", true, 0, "create", command(0, Map.of("frameNo", " "))));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM engineering_resource_maintenance", Integer.class));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM engineering_screen_frame", Integer.class));
        run("maker", true, 0, "create", command(0, Map.of("frameNo", "WK-001")));
        assertEquals(409, assertThrows(BusinessException.class,
                () -> run("maker", true, 0, "create", command(0, Map.of("frameNo", "WK-001")))).getCode());
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM engineering_resource_maintenance", Integer.class));
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM engineering_screen_frame", Integer.class));
    }

    @Test void invalidRevisionAndVersionCannotLeavePartialStretchOrPlate() {
        long frameId = ((Number) run("maker", true, 0, "create", command(0, Map.of("frameNo", "WK-001"))).get("frameId")).longValue();
        assertThrows(BusinessException.class, () -> run("maker", true, frameId, "stretch",
                command(5, Map.of("mesh", 300, "tensionNcm", 20, "qualified", true))));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM print_frame_stretch", Integer.class));
        long stretchId = ((Number) run("maker", true, frameId, "stretch",
                command(0, Map.of("mesh", 300, "tensionNcm", 20, "qualified", true))).get("stretchId")).longValue();
        assertThrows(BusinessException.class, () -> run("maker", false, 0, "create", command(1,
                Map.of("frameId", frameId, "stretchId", stretchId, "versionId", 12, "filmId", 22, "plateNo", "BAD", "position", "正面"))));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM engineering_screen_plate", Integer.class));
        assertEquals(2, db.queryForObject("SELECT COUNT(*) FROM engineering_resource_maintenance", Integer.class));
        assertEquals(1, db.queryForObject("SELECT revision FROM engineering_screen_frame", Integer.class));
    }
}
