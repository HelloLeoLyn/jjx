package com.jjx.engineering.printing.screen;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.system.utils.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

/** H2事务行为自查；不替代真实角色与MySQL/浏览器验收。 */
class ScreenRemarkTest {
    final ObjectMapper json = new ObjectMapper();
    JdbcTemplate db;
    ScreenService service;
    TransactionTemplate tx;

    @BeforeEach void setup() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        db = new JdbcTemplate(source);
        tx = new TransactionTemplate(new DataSourceTransactionManager(source));
        service = new ScreenService(new ScreenRepository(db), json);
        db.execute("CREATE TABLE engineering_screen_frame(frame_id BIGINT PRIMARY KEY,frame_no VARCHAR(50),frame_type VARCHAR(30),location VARCHAR(200),status VARCHAR(20),revision INT,remark VARCHAR(500),v2_registered INT,del_flag CHAR,update_by VARCHAR(64))");
        db.execute("CREATE TABLE engineering_resource_maintenance(maintenance_id BIGINT AUTO_INCREMENT PRIMARY KEY,resource_type VARCHAR(30),resource_id BIGINT,action_type VARCHAR(30),operator VARCHAR(64),request_id VARCHAR(64) UNIQUE,request_hash VARCHAR(64),result_snapshot VARCHAR(8000),before_status VARCHAR(20),after_status VARCHAR(20),description VARCHAR(2000),operate_time TIMESTAMP)");
        db.execute("CREATE TABLE print_frame_stretch(stretch_id BIGINT,frame_id BIGINT,open_frame_id BIGINT,mesh INT,tension_n_cm DECIMAL(10,2),qualified INT,end_time TIMESTAMP,material VARCHAR(200),stretch_time TIMESTAMP)");
        db.execute("CREATE TABLE engineering_screen_plate(plate_id BIGINT,occupied_frame_id BIGINT,frame_id BIGINT,version_id BIGINT,stretch_id BIGINT,v2_registered INT,status VARCHAR(20),washed_time TIMESTAMP,position VARCHAR(64))");
        db.execute("CREATE TABLE print_artwork_version(version_id BIGINT,status VARCHAR(20))");
        db.update("INSERT INTO engineering_screen_frame VALUES(1,'K1','原型号','A架','READY',0,'旧备注',1,'0',NULL)");
    }

    ScreenCommands command(String request, int revision, String remark) {
        return json.convertValue(java.util.Map.of("requestId", request, "expectedRevision", revision, "remark", remark), ScreenCommands.class);
    }

    java.util.Map<String,Object> save(ScreenCommands command) {
        try (var user = mockStatic(SecurityUtils.class)) {
            user.when(SecurityUtils::getUsername).thenReturn("remark-test");
            return tx.execute(status -> service.execute(true,1,"remark",command));
        }
    }

    @Test void saveReopenClearAndReplayPreserveIdentityAndAudit() throws Exception {
        var c = command("remark-1",0,"存放B架，注意防潮");
        assertEquals("存放B架，注意防潮",save(c).get("remark"));
        save(c); // 同命令重放不增加revision或履历。
        assertEquals(1,db.queryForObject("SELECT revision FROM engineering_screen_frame",Integer.class));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM engineering_resource_maintenance",Integer.class));
        var audit=json.readTree(db.queryForObject("SELECT description FROM engineering_resource_maintenance",String.class));
        assertEquals("旧备注",audit.path("beforeRemark").asText());
        assertEquals("存放B架，注意防潮",audit.path("afterRemark").asText());
        assertEquals("存放B架，注意防潮",new ScreenView(new ScreenRepository(db)).get(true,1).get("remark"));
        save(command("remark-2",1,""));
        var row=db.queryForMap("SELECT * FROM engineering_screen_frame");
        assertEquals("",row.get("remark"));
        assertEquals("K1",row.get("frame_no"));
        assertEquals("原型号",row.get("frame_type"));
        assertEquals("A架",row.get("location"));
        assertEquals("READY",row.get("status"));
    }

    @Test void staleRevisionAndOversizedRemarkLeaveNoChangesOrAudit() {
        save(command("first",0,"别人刚修改"));
        assertThrows(BusinessException.class,()->save(command("stale",0,"过期覆盖")));
        assertThrows(BusinessException.class,()->save(command("long",1,"字".repeat(501))));
        assertEquals("别人刚修改",db.queryForObject("SELECT remark FROM engineering_screen_frame",String.class));
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM engineering_resource_maintenance",Integer.class));
    }

    @Test void endpointRequiresResourceEditPermission() throws Exception {
        var permission=ScreenController.class.getMethod("editFrameRemark",long.class,ScreenCommands.class)
                .getAnnotation(SaCheckPermission.class);
        assertArrayEquals(new String[]{"engineering:resource:edit"},permission.value());
    }

    @Test void usableCountExcludesWrongParametersMaintenanceAndDisabledVersion() {
        db.update("INSERT INTO print_artwork_version VALUES(3,'RELEASED')");
        db.update("INSERT INTO print_frame_stretch VALUES(2,1,1,120,20,1,NULL,NULL,NULL)");
        db.update("INSERT INTO engineering_screen_plate VALUES(4,1,1,3,2,1,'RELEASED',NULL,'面板')");
        assertEquals(1,service.countUsablePlate(3,"{\"meshMin\":100,\"position\":\"面板\"}"));
        assertEquals(0,service.countUsablePlate(3,"{\"meshMin\":121}"));
        assertEquals(0,service.countUsablePlate(3,"{\"position\":\"下线\"}"));
        db.update("UPDATE engineering_screen_frame SET status='MAINTENANCE'");
        assertEquals(0,service.countUsablePlate(3));
        db.update("UPDATE engineering_screen_frame SET status='READY'");
        db.update("UPDATE print_artwork_version SET status='DISABLED'");
        assertEquals(0,service.countUsablePlate(3));
    }
}
