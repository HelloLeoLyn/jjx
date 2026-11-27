package com.jjx.engineering.printing.artwork;

import com.jjx.common.exception.BusinessException;
import com.jjx.system.domain.entity.SysAttachment;
import com.jjx.system.service.AttachmentAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.DriverManager;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ArtworkContractTest {
    @Test
    void viewPermissionCannotApproveOrDisposeOfFilm() throws Exception {
        var original=cn.dev33.satoken.stp.StpUtil.getStpLogic();
        var logic=org.mockito.Mockito.spy(new cn.dev33.satoken.stp.StpLogic("login"));
        org.mockito.Mockito.doReturn(42L).when(logic).getLoginId();
        org.mockito.Mockito.doReturn(java.util.List.of("engineering:film:view")).when(logic).getPermissionList(org.mockito.ArgumentMatchers.any());
        cn.dev33.satoken.stp.StpUtil.setStpLogic(logic);
        try {
            var service=org.mockito.Mockito.mock(ArtworkService.class);
            var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new ArtworkController(null,service))
                .addInterceptors(new cn.dev33.satoken.interceptor.SaInterceptor()).build();
            for(String path:java.util.List.of("/versions/1/approve","/versions/1/disable","/films/1/archive","/films/1/destroy","/films/1/send","/films/1/receive","/versions/1/films")) {
                mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/engineering/printing"+path)
                        .contentType("application/json").content("{}"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
            }
            org.mockito.Mockito.verifyNoInteractions(service);
        } finally {
            cn.dev33.satoken.stp.StpUtil.setStpLogic(original);
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "JJX_P03_TEST_PASSWORD", matches = ".+")
    void readsSharedProductsAndProtectsVersionDrawing() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:mysql://127.0.0.1/jjx_erp_db",
                "root", System.getenv("JJX_P03_TEST_PASSWORD"))) {
            var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            assertEquals("jjx_erp_db", jdbc.queryForObject("SELECT DATABASE()", String.class));
            connection.setAutoCommit(false);
            try {
                long product = -Math.abs(UUID.randomUUID().getMostSignificantBits() / 2);
                jdbc.update("INSERT INTO product(product_id,product_code,product_name) VALUES (?,uuid(),'样品A'),(?,uuid(),'样品B')", product, product+1);
                jdbc.update("INSERT INTO sys_attachment(file_name,file_path) VALUES ('drawing.pdf',?)",
                    "/p03-fixture/" + UUID.randomUUID());
                long attachment = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
                var repo = new ArtworkRepository(jdbc);
                var service = new ArtworkService(jdbc,new com.fasterxml.jackson.databind.ObjectMapper(),null);
                for (int i = 0; i < 2; i++) {
                    jdbc.update("INSERT INTO print_artwork(artwork_no,name,family) VALUES (?,?,?)",
                        UUID.randomUUID().toString(), "测试", "面板");
                    long artwork = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
                    jdbc.update("INSERT INTO print_artwork_version(artwork_id,version_no,status,drawing_file_id) VALUES (?,'v2','RELEASED',?)", artwork, attachment);
                    long version = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
                    jdbc.update("UPDATE print_artwork SET default_version_id=? WHERE artwork_id=?", version, artwork);
                    jdbc.update("INSERT INTO engineering_resource_product_rel(resource_type,resource_id,product_id) VALUES ('ARTWORK',?,?),('ARTWORK',?,?)",
                        artwork, product, artwork, product+1);
                    var view = repo.detail(artwork);
                    assertEquals(2, view.productIds().size());
                    assertEquals(version, view.defaultVersionId());
                    assertEquals("v2", view.versions().get(0).versionNo());
                    service.requireUsableVersion(version);
                    jdbc.update("INSERT INTO print_artwork_version(artwork_id,version_no,status) VALUES (?,'v3','RELEASED')", artwork);
                    long next = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
                    jdbc.update("UPDATE print_artwork SET default_version_id=? WHERE artwork_id=?", next, artwork);
                    service.requireUsableVersion(version);
                    jdbc.update("UPDATE print_artwork_version SET status='DISABLED' WHERE version_id=?", version);
                    assertThrows(BusinessException.class, () -> service.requireUsableVersion(version));
                    assertEquals("DISABLED", repo.detail(artwork).versions().get(0).status());
                }
                assertEquals(2, repo.productVersions(product, true).size());
                assertEquals(4, repo.productVersions(product+1, false).size());
                var file = new SysAttachment();
                file.setId(attachment);
                var access = new AttachmentAccessService(null, null, jdbc);
                assertThrows(BusinessException.class, () -> access.checkUnlink(file));
            } finally {
                connection.rollback();
            }
        }
    }
    @Test
    @EnabledIfEnvironmentVariable(named="JJX_P03_TEST_PASSWORD",matches=".+")
    void commandsSeparateReleaseActivationAndPhysicalCopies() throws Exception {
        try(var conn=DriverManager.getConnection("jdbc:mysql://127.0.0.1/jjx_erp_db","root",System.getenv("JJX_P03_TEST_PASSWORD"));
            var session=org.mockito.Mockito.mockStatic(com.jjx.system.utils.SecurityUtils.class)) {
            conn.setAutoCommit(false);
            try {
                var jdbc=new JdbcTemplate(new SingleConnectionDataSource(conn,true));
                var json=new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
                var attachments=org.mockito.Mockito.mock(com.jjx.system.service.AttachmentRefService.class);
                org.mockito.Mockito.when(attachments.create(org.mockito.ArgumentMatchers.any())).thenAnswer(call -> {
                    com.jjx.system.domain.dto.AttachmentRefDTO r=call.getArgument(0);
                    jdbc.update("insert into sys_attachment_ref(attachment_id,source_type,source_id) values (?,?,?)",
                        r.getAttachmentId(),r.getSourceType(),r.getSourceId());
                    return jdbc.queryForObject("select last_insert_id()",Long.class);
                });
                var svc=new ArtworkService(jdbc,json,attachments);
                String tag="P03-"+UUID.randomUUID();
                long product=-Math.abs(UUID.randomUUID().getMostSignificantBits()/2);
                jdbc.update("insert into product(product_id,product_code,product_name) values (?,uuid(),'样品')",product);
                jdbc.update("insert into sys_attachment(file_name,file_path) values ('test.pdf',?)",tag);
                long file=jdbc.queryForObject("select last_insert_id()",Long.class);
                session.when(com.jjx.system.utils.SecurityUtils::getUsername).thenReturn("designer");
                class Cmd {
                    ArtworkCommands make(int rev,java.util.Map<String,Object> fields) {
                        var body=new java.util.HashMap<>(fields);
                        body.put("requestId",UUID.randomUUID().toString());
                        body.put("expectedRevision",rev);
                        return json.convertValue(body,ArtworkCommands.class);
                    }
                }
                var cmd=new Cmd();
                var create=cmd.make(0,java.util.Map.of("artworkNo",tag,"name","图案","family","面板","productIds",java.util.List.of(product)));
                long art=((Number)svc.execute("createArtwork",0,create).get("artworkId")).longValue();
                assertEquals(art,((Number)svc.execute("createArtwork",0,create).get("artworkId")).longValue());
                long v=((Number)svc.execute("createVersion",art,cmd.make(0,java.util.Map.of("versionNo","A","drawingFileId",file))).get("versionId")).longValue();
                svc.execute("submit",v,cmd.make(0,java.util.Map.of()));
                assertEquals(403,assertThrows(BusinessException.class,()->svc.execute("approve",v,cmd.make(1,java.util.Map.of()))).getCode());
                session.when(com.jjx.system.utils.SecurityUtils::getUsername).thenReturn("qc");
                svc.execute("reject",v,cmd.make(1,java.util.Map.of("reason","修改")));
                session.when(com.jjx.system.utils.SecurityUtils::getUsername).thenReturn("designer");
                svc.execute("submit",v,cmd.make(2,java.util.Map.of()));
                session.when(com.jjx.system.utils.SecurityUtils::getUsername).thenReturn("qc");
                svc.execute("approve",v,cmd.make(3,java.util.Map.of()));
                assertNull(svc.execute("release",v,cmd.make(4,java.util.Map.of())).get("defaultVersionId"));
                assertEquals(v,svc.execute("activate",v,cmd.make(1,java.util.Map.of())).get("defaultVersionId"));
                assertThrows(BusinessException.class,()->svc.execute("editVersion",v,cmd.make(6,java.util.Map.of("versionNo","X"))));
                var numbers=org.mockito.Mockito.mock(com.jjx.framework.common.RedisSequenceService.class);
                var sequence=new java.util.concurrent.atomic.AtomicInteger();
                org.mockito.Mockito.when(numbers.generateBusinessNumberByType("PRINT_FILM_COPY","FL-","yyMM'-'",3))
                        .thenAnswer(call -> tag+"-"+sequence.incrementAndGet());
                org.springframework.test.util.ReflectionTestUtils.setField(svc,"filmNumbers",numbers);
                var demand=svc.execute("requestFilms",v,cmd.make(6,java.util.Map.of("quantity",2,
                        "dueDate",java.time.LocalDate.now().plusDays(7).toString(),"material","PET","size","A4","thicknessMm",0.1)));
                int index=0;
                for(Object value:(java.util.List<?>)demand.get("filmIds")) {
                    long film=((Number)value).longValue();
                    svc.execute("sendFilm",film,cmd.make(0,java.util.Map.of()));
                    svc.execute("receiveFilm",film,cmd.make(1,java.util.Map.of("filmNo",tag+index,"location","测试架",
                            "material","PET","size","A4","thicknessMm",0.1)));
                    if(index++==0) {
                        svc.execute("archive",film,cmd.make(2,java.util.Map.of("reason","封存")));
                        svc.execute("destroy",film,cmd.make(3,java.util.Map.of("reason","销毁")));
                    }
                }
                var view=new ArtworkRepository(jdbc).detail(art);
                assertEquals(2,view.films().size());
                assertEquals("DESTROYED",view.films().getFirst().status());
                assertEquals("AVAILABLE",view.films().getLast().status());
                assertEquals(409,assertThrows(BusinessException.class,()->svc.impact(v)).getCode());
            } finally {
                conn.rollback();
            }
        }
    }
}
