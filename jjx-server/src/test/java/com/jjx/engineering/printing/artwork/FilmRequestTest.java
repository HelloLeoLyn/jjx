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
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** 实际H2事务及双连接并发；仅元数据就绪检查替换为夹具已建，不冒充MySQL迁移验证。 */
class FilmRequestTest {
    final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    JdbcTemplate db;
    FilmRequestService service;
    TransactionTemplate tx;
    @BeforeEach void setup() {
        var source=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000","sa","");
        db=spy(new JdbcTemplate(source));
        doReturn(2).when(db).queryForObject(contains("information_schema.COLUMNS"),eq(Integer.class));
        tx=new TransactionTemplate(new DataSourceTransactionManager(source));tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
        var numbers=mock(RedisSequenceService.class);var sequence=new AtomicInteger();
        when(numbers.generateBusinessNumberByType("PRINT_FILM_REQUEST","CP-","yyMM'-'",3)).thenAnswer(call->"CP-"+sequence.incrementAndGet());
        service=new FilmRequestService(db,json,numbers,new ArtworkService(db,json,null));
        db.execute("CREATE TABLE print_artwork(artwork_id BIGINT PRIMARY KEY)");
        db.execute("CREATE TABLE print_artwork_version(version_id BIGINT PRIMARY KEY,artwork_id BIGINT,status VARCHAR(24),lock_version INT,FOREIGN KEY(artwork_id) REFERENCES print_artwork)");
        db.execute("CREATE TABLE print_film_request(request_id BIGINT AUTO_INCREMENT PRIMARY KEY,request_no VARCHAR(64) UNIQUE,version_id BIGINT NOT NULL,requested_quantity INT CHECK(requested_quantity BETWEEN 1 AND 500),material VARCHAR(200),thickness_mm DECIMAL(10,3) CHECK(thickness_mm>0),size VARCHAR(100),due_date DATE,remark VARCHAR(500),lock_version INT DEFAULT 0,create_by VARCHAR(64),create_time TIMESTAMP,UNIQUE(request_id,version_id),FOREIGN KEY(version_id) REFERENCES print_artwork_version)");
        db.execute("CREATE TABLE print_film_copy(film_id BIGINT AUTO_INCREMENT PRIMARY KEY,film_no VARCHAR_IGNORECASE(64) UNIQUE,version_id BIGINT,film_request_id BIGINT,location VARCHAR(200),status VARCHAR(24),material VARCHAR(200),thickness_mm DECIMAL(10,3),size VARCHAR(100),output_time TIMESTAMP,create_by VARCHAR(64),create_time TIMESTAMP,FOREIGN KEY(film_request_id,version_id) REFERENCES print_film_request(request_id,version_id))");
        db.execute("CREATE TABLE engineering_resource_maintenance(maintenance_id BIGINT AUTO_INCREMENT PRIMARY KEY,resource_type VARCHAR(20),resource_id BIGINT,action_type VARCHAR(20),operator VARCHAR(64),request_id VARCHAR(64) UNIQUE,request_hash VARCHAR(64),result_snapshot VARCHAR(16000),description VARCHAR(500),after_status VARCHAR(20))");
        db.update("INSERT INTO print_artwork VALUES(1),(2)");
        db.update("INSERT INTO print_artwork_version VALUES(11,1,'RELEASED',0),(21,2,'RELEASED',0),(12,1,'DRAFT',0)");
        db.execute("ALTER TABLE print_artwork ADD number_series VARCHAR(64) DEFAULT 'GY'");
        db.execute("CREATE TABLE sys_number_sequence(sequence_key VARCHAR(100),period_key VARCHAR(64),current_value BIGINT,create_time TIMESTAMP,update_time TIMESTAMP,UNIQUE(sequence_key,period_key))");
        org.springframework.test.util.ReflectionTestUtils.setField(service,"styles",new PrintStyleService(db,json));
    }
    FilmRequestCommands command(String rid,int revision,Map<String,Object> fields) {
        var body=new HashMap<>(fields);body.put("requestId",rid);body.put("expectedRevision",revision);
        return json.convertValue(body,FilmRequestCommands.class);
    }
    FilmRequestCommands application(String rid,int quantity) {return command(rid,0,Map.of("quantity",quantity,"material","PET","thicknessMm",0.125,"size","A4","dueDate","2026-12-01"));}
    FilmRequestCommands receipt(String rid,int revision,String... names) {
        return command(rid,revision,Map.of("copies",Arrays.stream(names).map(name->Map.of("filmNo",name,"location","A架")).toList()));
    }
    Map<String,Object> run(boolean receive,long target,FilmRequestCommands command) {
        try(var user=mockStatic(SecurityUtils.class)) {
            user.when(SecurityUtils::getUsername).thenReturn("film-request-test");
            return tx.execute(status->service.execute(receive,target,command));
        }
    }
    long apply(String rid,int quantity) {return ((Number)run(false,11,application(rid,quantity)).get("requestId")).longValue();}

    @Test void automaticNumbersContinueAcrossPartialReceiptsAndSkipManualCollision() {
        long id=apply("auto-application",3);
        run(true,id,receipt("manual",0,"GY-FL-001"));
        run(true,id,receipt("auto-one",1,""));
        run(true,id,receipt("auto-two",2,""));
        assertEquals(List.of("GY-FL-001","GY-FL-002","GY-FL-003"),db.queryForList("SELECT film_no FROM print_film_copy ORDER BY film_id",String.class));
        assertEquals(3,received(id));
        run(true,id,receipt("auto-two",2,""));
        assertEquals(3,received(id));
    }

    @Test void applicationAndTwoPartialReceiptsKeepSeparateFactsAndReplay() {
        var application=application("application",2);long id=((Number)run(false,11,application).get("requestId")).longValue();
        run(false,11,application);
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM print_film_request",Integer.class));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM print_film_copy",Integer.class));
        assertEquals("PENDING",service.list(1).getFirst().progress());
        var first=receipt("first",0,"FL-1");run(true,id,first);run(true,id,first);
        assertEquals(1,service.list(1).getFirst().receivedQuantity());
        assertEquals("PARTIAL",service.list(1).getFirst().progress());
        run(true,id,receipt("second",1,"FL-2"));
        var result=service.list(1).getFirst();
        assertEquals(2,result.requestedQuantity());assertEquals(2,result.receivedQuantity());assertEquals(0,result.remainingQuantity());
        assertEquals("RECEIVED",result.progress());assertTrue(service.list(2).isEmpty());
        assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM print_film_copy WHERE film_request_id=? AND version_id=11 AND status='AVAILABLE'",Integer.class,id));
        assertEquals(0,db.queryForObject("SELECT lock_version FROM print_artwork_version WHERE version_id=11",Integer.class));
    }
    @Test void duplicateSecondCopyRollsBackWholeReceiptAndAudit() {
        long first=apply("first-application",1);run(true,first,receipt("existing",0,"EXISTING"));
        long id=apply("application",2);
        assertThrows(BusinessException.class,()->run(true,id,receipt("bad-batch",0,"NEW","existing")));
        assertEquals(0,received(id));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM print_film_copy WHERE film_no='NEW'",Integer.class));
        assertEquals(0,db.queryForObject("SELECT lock_version FROM print_film_request WHERE request_id=?",Integer.class,id));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM engineering_resource_maintenance WHERE request_id='bad-batch'",Integer.class));
    }
    private int received(long id) {return db.queryForObject("SELECT COUNT(*) FROM print_film_copy WHERE film_request_id=?",Integer.class,id);}
    @Test void staleRevisionOverReceiptAndDuplicateBatchCannotAddCopies() {
        long id=apply("application",2);run(true,id,receipt("first",0,"A"));
        assertThrows(BusinessException.class,()->run(true,id,receipt("stale",0,"B")));
        assertThrows(BusinessException.class,()->run(true,id,receipt("too-many",1,"B","C")));
        long other=apply("other",2);
        assertThrows(BusinessException.class,()->run(true,other,receipt("same-batch",0,"D","d")));
        assertEquals(1,received(id));assertEquals(0,received(other));
    }
    @Test void missingFieldsWrongVersionAndPayloadReuseAreRejected() {
        assertThrows(BusinessException.class,()->run(false,12,application("draft",1)));
        assertThrows(BusinessException.class,()->run(false,11,application("zero",0)));
        long id=apply("application",2);
        assertThrows(BusinessException.class,()->run(false,11,application("application",3)));
        var fractional=json.convertValue(Map.of("requestId","fraction","expectedRevision",0,"quantity",1.5,"material","PET","thicknessMm",0.125,"size","A4","dueDate","2026-12-01"),FilmRequestCommands.class);
        assertThrows(BusinessException.class,()->run(false,11,fractional));
        assertThrows(BusinessException.class,()->run(true,id,command("empty",0,Map.of("copies",List.of(Map.of("filmNo","A","location",""))))));
        assertThrows(Exception.class,()->json.readValue("{\"requestId\":\"a\",\"expectedRevision\":0,\"versionId\":21}",FilmRequestCommands.class));
        assertEquals(0,received(id));
    }
    @Test void compositeForeignKeyRejectsCrossVersionPhysicalCopy() {
        long id=apply("application",1);
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->db.update("INSERT INTO print_film_copy(film_no,version_id,film_request_id) VALUES('WRONG',21,?)",id));
    }
    @Test void parallelReceiptsCannotBothTakeTheLastCopy() throws Exception {
        long id=apply("application",1);
        ExecutorService executor=Executors.newFixedThreadPool(2);CountDownLatch start=new CountDownLatch(1);
        try {
            List<Future<Boolean>> results=new ArrayList<>();
            for(int i=0;i<2;i++) {final int number=i;results.add(executor.submit(()->{
                start.await();try {run(true,id,receipt("parallel-"+number,0,"PARALLEL-"+number));return true;}
                catch(BusinessException conflict) {assertEquals(409,conflict.getCode());return false;}
            }));}
            start.countDown();int success=0;for(var result:results) if(result.get(10,TimeUnit.SECONDS)) success++;
            assertEquals(1,success);assertEquals(1,received(id));
        } finally {executor.shutdown();assertTrue(executor.awaitTermination(10,TimeUnit.SECONDS));}
    }
    @Test void schemaNotDeployedFailsClearlyWithoutPretendingEmptyData() {
        doReturn(0).when(db).queryForObject(contains("information_schema.COLUMNS"),eq(Integer.class));
        assertTrue(assertThrows(BusinessException.class,()->service.list(1)).getMessage().contains("迁移399"));
        assertThrows(BusinessException.class,()->run(false,11,application("no-schema",1)));
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM engineering_resource_maintenance",Integer.class));
    }
    @Test void viewPermissionCannotApplyOrReceive() throws Exception {
        var original=cn.dev33.satoken.stp.StpUtil.getStpLogic();
        var logic=spy(new cn.dev33.satoken.stp.StpLogic("login"));
        doReturn(42L).when(logic).getLoginId();doReturn(List.of("engineering:film:view")).when(logic).getPermissionList(any());
        cn.dev33.satoken.stp.StpUtil.setStpLogic(logic);
        try {
            var commands=mock(FilmRequestService.class);
            var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new FilmRequestController(commands))
                    .addInterceptors(new cn.dev33.satoken.interceptor.SaInterceptor()).build();
            for(String path:List.of("/engineering/printing/versions/11/film-requests","/engineering/printing/film-requests/1/receipts"))
                mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path).contentType("application/json").content("{}"))
                        .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
            verifyNoInteractions(commands);
        } finally {cn.dev33.satoken.stp.StpUtil.setStpLogic(original);}
    }

}
