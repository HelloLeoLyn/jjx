package com.jjx.engineering.printing.screen;

import com.jjx.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.system.utils.SecurityUtils;
import com.jjx.engineering.service.EngineeringResourceService;
import com.jjx.engineering.controller.EngineeringResourceController;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import java.util.UUID;
import java.util.Map;
import static org.mockito.Mockito.mockStatic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import static com.jjx.engineering.printing.screen.ScreenService.PlateStatus.*;
import static com.jjx.engineering.printing.screen.ScreenService.FrameStatus.READY;
import static org.junit.jupiter.api.Assertions.*;

class ScreenLifecycleTest {
    private final ScreenService service = new ScreenService(null,null);

    @Test
    void failedInspectionCanBeRepeatedUntilRelease() {
        assertEquals(FAILED, service.inspect(PENDING, false));
        assertEquals(RELEASED, service.inspect(FAILED, true));
        assertThrows(BusinessException.class, () -> service.inspect(WASHED, true));
    }

    @Test
    void activeUseBlocksRetirementAndWashedPlateIsTerminal() {
        assertThrows(BusinessException.class, () -> service.retire(RELEASED, WASHED, true));
        assertEquals(WASHED, service.retire(RELEASED, WASHED, false));
        assertThrows(BusinessException.class, () -> service.retire(WASHED, SCRAPPED, false));
    }

    @Test
    void creationRequiresCurrentQualifiedStretchAndMatchingFilm() {
        assertThrows(BusinessException.class, () -> service.requirePlateCreation(READY, false, false, true, true));
        assertThrows(BusinessException.class, () -> service.requirePlateCreation(READY, false, true, true, false));
        assertDoesNotThrow(() -> service.requirePlateCreation(READY, false, true, true, true));
    }
    @Test
    @EnabledIfEnvironmentVariable(named="P04_TEST_DB_URL", matches=".+")
    void lifecycleThroughMvcWithRollback() {
        var source = new DriverManagerDataSource(System.getenv("P04_TEST_DB_URL"),
                System.getenv("P04_TEST_DB_USER"),System.getenv("P04_TEST_DB_PASSWORD"));
        var jdbc = new JdbcTemplate(source);
        var repo = new ScreenRepository(jdbc);
        var json = new ObjectMapper();
        var mvc = MockMvcBuilders.standaloneSetup(new ScreenController(new ScreenService(repo,json),new ScreenView(repo),repo),
                new EngineeringResourceController(new EngineeringResourceService(jdbc))).build();
        var tx = new TransactionTemplate(new DataSourceTransactionManager(source));
        tx.executeWithoutResult(transaction -> {
            transaction.setRollbackOnly();
            String tag = "P04-" + UUID.randomUUID();
            try (var security = mockStatic(SecurityUtils.class)) {
                security.when(SecurityUtils::getUsername).thenReturn("制版测试员");
                jdbc.update("insert into print_artwork(artwork_no,name,family) values (?,?,'PANEL')",tag,tag);
                long artwork = repo.insertedId();
                jdbc.update("insert into print_artwork_version(artwork_id,version_no,status) values (?,'1','RELEASED')",artwork);
                long version = repo.insertedId();
                jdbc.update("insert into print_film_copy(film_no,version_id,status,thickness_mm) values (?,?,'AVAILABLE',1)",tag,version);
                long film = repo.insertedId();
                class Api {
                    String payload(int revision, Map<String,Object> fields) throws Exception {
                        var body = new java.util.HashMap<>(fields);
                        body.put("requestId",UUID.randomUUID().toString());
                        body.put("expectedRevision",revision);
                        return json.writeValueAsString(body);
                    }
                    com.fasterxml.jackson.databind.JsonNode call(String path,String body) throws Exception {
                        var response = mvc.perform(post("/engineering/printing/"+path).contentType(MediaType.APPLICATION_JSON)
                                .content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                                .andReturn().getResponse().getContentAsByteArray();
                        return json.readTree(response).get("data");
                    }
                }
                var api = new Api();
                String create = api.payload(0,Map.of("frameNo",tag));
                long frame = api.call("frames",create).get("frameId").asLong();
                assertEquals(frame,api.call("frames",create).get("frameId").asLong());
                String frames = "frames/"+frame+"/";
                long stretch = api.call(frames+"stretch",api.payload(0,Map.of("mesh",120,"tensionNcm",12,
                        "qualified",true))).get("stretchId").asLong();
                Map<String,Object> plate = Map.of("frameId",frame,"stretchId",stretch,"versionId",version,
                        "filmId",film,"position","正面","plateNo",tag);
                long id = api.call("plates",api.payload(1,plate)).get("plateId").asLong();
                String plates = "plates/"+id+"/";
                assertEquals(id,api.call(frames+"clean",api.payload(2,Map.of("reason","清洁"))).get("plateId").asLong());
                api.call(plates+"fail",api.payload(0,Map.of("inspection","张力复核")));
                security.when(SecurityUtils::getUsername).thenReturn("检验测试员");
                api.call(plates+"release",api.payload(1,Map.of("inspection","复检合格")));
                api.call(plates+"wash",api.payload(2,Map.of("reason","换图案")));
                mvc.perform(get("/engineering/printing/plates/"+id)).andExpect(jsonPath("$.data.status").value("WASHED"))
                        .andExpect(jsonPath("$.data.history.length()").value(4));
                var replacement = new java.util.HashMap<>(plate);
                jdbc.update("insert into print_artwork(artwork_no,name,family) values (?,?,'PANEL')",tag+"-2",tag);
                long otherArtwork = repo.insertedId();
                jdbc.update("insert into print_artwork_version(artwork_id,version_no,status) values (?,'1','RELEASED')",otherArtwork);
                long otherVersion = repo.insertedId();
                jdbc.update("insert into print_film_copy(film_no,version_id,status,thickness_mm) values (?,?,'AVAILABLE',1)",tag+"-2",otherVersion);
                replacement.put("filmId",repo.insertedId());
                replacement.put("versionId",otherVersion);
                replacement.put("plateNo",tag+"-2");
                assertNotEquals(id,api.call("plates",api.payload(4,replacement)).get("plateId").asLong());
                long secondId = ((Number)repo.one("select plate_id from engineering_screen_plate where plate_no=?",tag+"-2").get("plate_id")).longValue();
                api.call("plates/"+secondId+"/scrap",api.payload(0,Map.of("reason","版面报废")));
                api.call(frames+"maintain",api.payload(6,Map.of("reason","维护")));
                api.call(frames+"resume",api.payload(7,Map.of("reason","维护完成")));
                api.call(frames+"scrap",api.payload(8,Map.of("reason","框体报废")));
                mvc.perform(get("/engineering/printing/frames/"+frame)).andExpect(jsonPath("$.data.status").value("SCRAPPED"));
                mvc.perform(get("/engineering/resources/dies")).andExpect(jsonPath("$.code").value(200));
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
    }

    @Test
    @EnabledIfEnvironmentVariable(named="P04_TEST_DB_URL", matches=".+")
    void twoConnectionsCompeteForFrameAndActiveUseBlocksWash() throws Exception {
        var source = new DriverManagerDataSource(System.getenv("P04_TEST_DB_URL"),
                System.getenv("P04_TEST_DB_USER"),System.getenv("P04_TEST_DB_PASSWORD"));
        var jdbc = new JdbcTemplate(source);
        var tx = new TransactionTemplate(new DataSourceTransactionManager(source));
        var repo = new ScreenRepository(jdbc);
        var json = new ObjectMapper();
        var service = new ScreenService(repo,json);
        String tag = "P04-" + UUID.randomUUID();
        long[] ids = tx.execute(t -> {
            jdbc.update("insert into print_artwork(artwork_no,name,family) values (?,?,'PANEL')",tag,tag);
            long artwork = repo.insertedId();
            jdbc.update("insert into print_artwork_version(artwork_id,version_no,status) values (?,'1','RELEASED')",artwork);
            long version = repo.insertedId();
            jdbc.update("insert into print_film_copy(film_no,version_id,status,thickness_mm) values (?,?,'AVAILABLE',1)",tag,version);
            long film = repo.insertedId();
            jdbc.update("insert into engineering_screen_frame(frame_no,v2_registered,status) values (?,1,'READY')",tag);
            long frame = repo.insertedId();
            jdbc.update("insert into print_frame_stretch(frame_id,mesh,tension_n_cm,stretch_time,qualified) values (?,120,20,now(),1)",frame);
            return new long[]{artwork,version,film,frame,repo.insertedId()};
        });
        try (var workers = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var barrier = new java.util.concurrent.CyclicBarrier(2);
            java.util.concurrent.Callable<Long> contend = () -> {
                var command = json.convertValue(Map.of("requestId",UUID.randomUUID().toString(),"expectedRevision",0,
                        "frameId",ids[3],"stretchId",ids[4],"filmId",ids[2],"versionId",ids[1],
                        "plateNo",tag+Thread.currentThread().threadId(),"position","面板"),ScreenCommands.class);
                barrier.await(5,java.util.concurrent.TimeUnit.SECONDS);
                try (var security = mockStatic(SecurityUtils.class)) {
                    security.when(SecurityUtils::getUsername).thenReturn(tag);
                    var result = tx.execute(t -> service.execute(false,0,"create",command));
                    return ((Number)result.get("plateId")).longValue();
                } catch (BusinessException e) {
                    assertEquals(409,e.getCode());
                    return 0L;
                }
            };
            var first = workers.submit(contend);
            var second = workers.submit(contend);
            long a = first.get(15,java.util.concurrent.TimeUnit.SECONDS);
            long b = second.get(15,java.util.concurrent.TimeUnit.SECONDS);
            assertTrue((a==0) != (b==0));
            long plate = Math.max(a,b);
            var command = json.convertValue(Map.of("requestId",tag+"-wash","expectedRevision",1,"reason","换版"),ScreenCommands.class);
            try (var security = mockStatic(SecurityUtils.class)) {
                security.when(SecurityUtils::getUsername).thenReturn(tag+"-qc");
                tx.executeWithoutResult(t -> service.execute(false,plate,"release",json.convertValue(Map.of(
                        "requestId",tag+"-qc","expectedRevision",0,"inspection","合格"),ScreenCommands.class)));
            }
            var starting = new java.util.concurrent.CountDownLatch(1);
            var washAttempt = new java.util.concurrent.CountDownLatch(1);
            var start = workers.submit(() -> tx.executeWithoutResult(t -> {
                repo.one("select version_id from print_artwork_version where version_id=? for update",ids[1]);
                repo.frame(ids[3],true);
                assertEquals("RELEASED",repo.plate(plate,true).get("status"));
                jdbc.update("""
                    insert into print_use_segment(execution_id,spec_version_id,routing_item_id,version_id,plate_id,start_time,request_id)
                    values (?,?,?, ?,?,now(),?)
                    """,-plate,1,1,ids[1],plate,tag);
                starting.countDown();
                try {
                    assertTrue(washAttempt.await(5,java.util.concurrent.TimeUnit.SECONDS));
                } catch (InterruptedException ex) {
                    throw new AssertionError(ex);
                }
            }));
            assertTrue(starting.await(5,java.util.concurrent.TimeUnit.SECONDS));
            try (var security = mockStatic(SecurityUtils.class)) {
                security.when(SecurityUtils::getUsername).thenReturn(tag);
                washAttempt.countDown();
                var error = assertThrows(BusinessException.class,() -> tx.executeWithoutResult(t ->
                        service.execute(false,plate,"wash",command)));
                assertEquals(409,error.getCode());
                start.get(10,java.util.concurrent.TimeUnit.SECONDS);
                jdbc.update("update print_use_segment set end_time=now() where request_id=?",tag);
                tx.executeWithoutResult(t -> service.execute(false,plate,"wash",command));
            }
        } finally {
            tx.executeWithoutResult(t -> {
                jdbc.update("delete from print_use_segment where request_id=?",tag);
                jdbc.update("delete from engineering_resource_maintenance where operator in (?,?)",tag,tag+"-qc");
                jdbc.update("delete from engineering_screen_plate where frame_id=?",ids[3]);
                jdbc.update("delete from print_frame_stretch where stretch_id=?",ids[4]);
                jdbc.update("delete from engineering_screen_frame where frame_id=?",ids[3]);
                jdbc.update("delete from print_film_copy where film_id=?",ids[2]);
                jdbc.update("delete from print_artwork_version where version_id=?",ids[1]);
                jdbc.update("delete from print_artwork where artwork_id=?",ids[0]);
            });
        }
    }
}
