package com.jjx.engineering.printing.artwork;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.framework.common.RedisSequenceService;
import com.jjx.system.utils.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FilmDemandTest {
    final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    JdbcTemplate db;
    ArtworkService service;
    TransactionTemplate tx;

    @BeforeEach void setup() {
        var source=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");
        db=new JdbcTemplate(source);tx=new TransactionTemplate(new DataSourceTransactionManager(source));
        service=new ArtworkService(db,json,null);
        var numbers=mock(RedisSequenceService.class);
        var next=new AtomicInteger();
        when(numbers.generateBusinessNumberByType("PRINT_FILM_COPY","FL-","yyMM'-'",3))
                .thenAnswer(call->"FL-2611-"+String.format("%03d",next.incrementAndGet()));
        ReflectionTestUtils.setField(service,"filmNumbers",numbers);
        db.execute("CREATE TABLE print_artwork_version(version_id BIGINT PRIMARY KEY,status VARCHAR(24),lock_version INT)");
        db.execute("CREATE TABLE print_film_copy(film_id BIGINT AUTO_INCREMENT PRIMARY KEY,film_no VARCHAR(64) UNIQUE,version_id BIGINT,location VARCHAR(200),status VARCHAR(24),material VARCHAR(200),thickness_mm DECIMAL(10,3) CHECK(thickness_mm>0),size VARCHAR(100),output_time TIMESTAMP,destroy_time TIMESTAMP,lock_version INT DEFAULT 0,create_by VARCHAR(64),create_time TIMESTAMP,update_by VARCHAR(64),update_time TIMESTAMP)");
        db.execute("CREATE TABLE engineering_resource_maintenance(maintenance_id BIGINT AUTO_INCREMENT PRIMARY KEY,resource_type VARCHAR(20),resource_id BIGINT,action_type VARCHAR(20),operator VARCHAR(64),request_id VARCHAR(64) UNIQUE,request_hash VARCHAR(64),result_snapshot VARCHAR(12000),description VARCHAR(500),after_status VARCHAR(20))");
        db.update("INSERT INTO print_artwork_version VALUES(1,'RELEASED',0)");
    }
    ArtworkCommands command(String id,int revision,Map<String,Object> values) {
        Map<String,Object> body=new HashMap<>(values);body.put("requestId",id);body.put("expectedRevision",revision);
        return json.convertValue(body,ArtworkCommands.class);
    }
    Map<String,Object> run(String action,long id,ArtworkCommands command) {
        try(var user=mockStatic(SecurityUtils.class)) {
            user.when(SecurityUtils::getUsername).thenReturn("film-test");
            return tx.execute(status->service.execute(action,id,command));
        }
    }
    Map<String,Object> specs() { return Map.of("material","PET","thicknessMm",0.125,"size","300x200mm"); }
    ArtworkCommands demand(String request,int quantity) {
        var body=new HashMap<>(specs());body.put("quantity",quantity);body.put("dueDate","2026-12-01");
        return command(request,0,body);
    }
    @Test void batchDemandSendReceiveAndReplayKeepVersionAndPerSheetAudit() throws Exception {
        var request=demand("demand-1",2);
        var result=run("requestFilms",1,request);
        run("requestFilms",1,request);
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM print_film_copy",Integer.class));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM print_film_copy WHERE status='AVAILABLE'",Integer.class));
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM engineering_resource_maintenance WHERE action_type='requestDetails'",Integer.class));
        long film=((Number)result.get("filmId")).longValue();
        var sent=command("send-1",0,Map.of());run("sendFilm",film,sent);run("sendFilm",film,sent);
        assertEquals("SENT",db.queryForObject("SELECT status FROM print_film_copy WHERE film_id=?",String.class,film));
        var received=new HashMap<>(specs());received.put("filmNo","FL-ACTUAL");received.put("location","A架1格");received.put("material","PET回片");
        var receive=command("receive-1",1,received);run("receiveFilm",film,receive);run("receiveFilm",film,receive);
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM print_film_copy WHERE status='AVAILABLE'",Integer.class));
        assertEquals(0,db.queryForObject("SELECT lock_version FROM print_artwork_version",Integer.class));
        var audit=json.readTree(db.queryForObject("SELECT result_snapshot FROM engineering_resource_maintenance WHERE request_id='receive-1'",String.class));
        assertEquals("PET",audit.path("before").path("material").asText());
        assertEquals("PET回片",audit.path("received").path("material").asText());
        assertTrue(db.queryForList("SELECT result_snapshot FROM engineering_resource_maintenance WHERE action_type='requestDetails'",String.class)
                .stream().allMatch(raw->raw.contains("2026-12-01")));
    }
    @Test void wrongStateStaleRevisionAndUnavailableVersionRollBack() {
        long film=((Number)run("requestFilms",1,demand("request",1)).get("filmId")).longValue();
        var received=new HashMap<>(specs());received.put("filmNo","F1");received.put("location","A架");
        assertThrows(BusinessException.class,()->run("receiveFilm",film,command("early",0,received)));
        run("sendFilm",film,command("send",0,Map.of()));
        assertThrows(BusinessException.class,()->run("receiveFilm",film,command("stale",0,received)));
        db.update("UPDATE print_artwork_version SET status='DISABLED'");
        assertThrows(BusinessException.class,()->run("receiveFilm",film,command("disabled",1,received)));
        assertEquals("SENT",db.queryForObject("SELECT status FROM print_film_copy",String.class));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM engineering_resource_maintenance WHERE request_id IN ('early','stale','disabled')",Integer.class));
    }
    @Test void historicalRequestHashWithoutNewOptionalFieldsStillReplays() throws Exception {
        var command=command("old-archive",0,Map.of("reason","封存"));
        var old=(com.fasterxml.jackson.databind.node.ObjectNode)json.valueToTree(command);
        old.remove("quantity");old.remove("dueDate");
        String hash=com.jjx.common.utils.Sha256Utils.ofText(json.writeValueAsString(
                List.of("FILM_COPY","archive",42L,"film-test",old)));
        db.update("INSERT INTO engineering_resource_maintenance(resource_type,resource_id,action_type,operator,request_id,request_hash,result_snapshot) VALUES('FILM_COPY',42,'archive','film-test','old-archive',?,?)",
                hash,"{\"filmId\":42,\"status\":\"ARCHIVED\",\"revision\":1}");
        assertEquals("ARCHIVED",run("archive",42,command).get("status"));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM print_film_copy",Integer.class));
    }

    @Test void invalidDemandDoesNotCreatePhysicalCopies() {
        assertThrows(BusinessException.class,()->run("requestFilms",1,demand("zero",0)));
        assertThrows(BusinessException.class,()->run("requestFilms",1,command("date",0,specs())));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM print_film_copy",Integer.class));
    }
}
