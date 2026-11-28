package com.jjx.product.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.engineering.printing.artwork.ArtworkCommands;
import com.jjx.engineering.printing.artwork.ArtworkService;
import com.jjx.product.controller.FilmCreationController;
import com.jjx.system.domain.dto.AttachmentRefDTO;
import com.jjx.system.service.AttachmentRefService;
import com.jjx.system.utils.SecurityUtils;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 隔离 H2 执行原领域服务 SQL 和 Spring 事务；不代表真实 MySQL 或浏览器验收。 */
class FilmCreationServiceTest {
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    private <T> T transactional(T target, DriverManagerDataSource source) {
        var factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(source),
                new AnnotationTransactionAttributeSource()));
        @SuppressWarnings("unchecked") T proxy = (T) factory.getProxy();
        return proxy;
    }

    private DriverManagerDataSource database() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        var db = new JdbcTemplate(source);
        db.execute("CREATE TABLE product(product_id BIGINT PRIMARY KEY)");
        db.execute("CREATE TABLE print_artwork(artwork_id BIGINT AUTO_INCREMENT PRIMARY KEY, artwork_no VARCHAR(64) UNIQUE, name VARCHAR(200), purpose VARCHAR(200), family VARCHAR(20), set_code VARCHAR(64), sample_order_id BIGINT, create_by VARCHAR(64), create_time TIMESTAMP, lock_version INT DEFAULT 0)");
        db.execute("CREATE TABLE print_artwork_version(version_id BIGINT AUTO_INCREMENT PRIMARY KEY, artwork_id BIGINT, version_no VARCHAR(32), status VARCHAR(20), drawing_file_id BIGINT, technical_spec VARCHAR(2000), designer_id VARCHAR(64), create_by VARCHAR(64), create_time TIMESTAMP, lock_version INT DEFAULT 0,update_by VARCHAR(64),update_time TIMESTAMP, UNIQUE(artwork_id,version_no))");
        db.execute("CREATE TABLE engineering_resource_product_rel(resource_type VARCHAR(32), resource_id BIGINT, product_id BIGINT, create_by VARCHAR(64), update_by VARCHAR(64), is_active INT DEFAULT 1, UNIQUE(resource_type,resource_id,product_id))");
        db.execute("CREATE TABLE engineering_resource_maintenance(maintenance_id BIGINT AUTO_INCREMENT PRIMARY KEY, resource_type VARCHAR(32),resource_id BIGINT,action_type VARCHAR(32),operator VARCHAR(64),request_id VARCHAR(64) UNIQUE,request_hash VARCHAR(64),result_snapshot VARCHAR(8000),after_status VARCHAR(32),description VARCHAR(500))");
        db.execute("CREATE TABLE sys_attachment_ref(id BIGINT AUTO_INCREMENT PRIMARY KEY,attachment_id BIGINT,source_type VARCHAR(64),source_id BIGINT,document_type VARCHAR(100),deleted INT DEFAULT 0,ref_state VARCHAR(20) DEFAULT 'active')");
        db.update("INSERT INTO product VALUES (10)");
        return source;
    }

    private ArtworkCommands command(Map<String, Object> additions) {
        var fields = new HashMap<String, Object>();
        fields.put("requestId", UUID.randomUUID().toString());
        fields.put("expectedRevision", 0);
        fields.put("artworkNo", "PW-TEST");
        fields.put("name", "测试菲林");
        fields.put("family", "面板");
        fields.put("productIds", java.util.List.of(10));
        fields.putAll(additions);
        return json.convertValue(fields, ArtworkCommands.class);
    }

    private FilmCreationService service(DriverManagerDataSource source, AttachmentRefService attachments) {
        var domain = transactional(new ArtworkService(new JdbcTemplate(source), json, attachments), source);
        return transactional(new FilmCreationService(domain, json), source);
    }

    @Test
    void createsDraftWithDrawingAndReplaysWithoutDuplicatingEitherRecord() {
        var source = database();
        var db = new JdbcTemplate(source);
        var attachments = mock(AttachmentRefService.class);
        when(attachments.create(any())).thenAnswer(call -> {
            AttachmentRefDTO ref = call.getArgument(0);
            db.update("INSERT INTO sys_attachment_ref(attachment_id,source_type,source_id,document_type) VALUES (?,?,?,?)",
                    ref.getAttachmentId(), ref.getSourceType(), ref.getSourceId(), ref.getDocumentType());
            return 1L;
        });
        var service = service(source, attachments);
        var command = command(Map.of("drawingFileId", 55, "drawingDocumentType", "FILM", "technicalSpec", "定位要求"));
        try (var user = mockStatic(SecurityUtils.class)) {
            user.when(SecurityUtils::getUsername).thenReturn("designer");
            var result = service.create(command);
            assertEquals(result, service.create(command));
            var draft = db.queryForMap("SELECT * FROM print_artwork_version");
            assertEquals("FILM", db.queryForObject("SELECT document_type FROM sys_attachment_ref", String.class));
            assertEquals("V1", draft.get("version_no"));
            assertEquals("DRAFT", draft.get("status"));
            assertEquals(55L, draft.get("drawing_file_id"));
            assertEquals("定位要求", draft.get("technical_spec"));
            assertEquals(result.get("artworkId"), draft.get("artwork_id"));
            assertEquals(result.get("versionId"), db.queryForObject("SELECT source_id FROM sys_attachment_ref", Long.class));
            assertEquals("print_artwork_version", db.queryForObject("SELECT source_type FROM sys_attachment_ref", String.class));
            assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM print_artwork", Integer.class));
            assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM print_artwork_version", Integer.class));
            assertEquals(2, db.queryForObject("SELECT COUNT(*) FROM engineering_resource_maintenance", Integer.class));
            verify(attachments, times(1)).create(any());

            var changed = command(Map.of("requestId", command.requestId(), "drawingFileId", 56));
            assertEquals(409, assertThrows(BusinessException.class, () -> service.create(changed)).getCode());
            assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM print_artwork_version", Integer.class));
        }
    }

    @Test
    void editVersionPassesSelectedTypeToReplacementDrawingReference() {
        var source = database();
        var db = new JdbcTemplate(source);
        var attachments = mock(AttachmentRefService.class);
        when(attachments.create(any())).thenAnswer(call -> {
            AttachmentRefDTO ref = call.getArgument(0);
            db.update("INSERT INTO sys_attachment_ref(attachment_id,source_type,source_id,document_type) VALUES (?,?,?,?)",
                    ref.getAttachmentId(), ref.getSourceType(), ref.getSourceId(), ref.getDocumentType());
            return db.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        });
        var domain = transactional(new ArtworkService(db, json, attachments), source);
        var service = transactional(new FilmCreationService(domain, json), source);
        try (var user = mockStatic(SecurityUtils.class)) {
            user.when(SecurityUtils::getUsername).thenReturn("designer");
            var created = service.create(command(Map.of("drawingFileId", 55, "drawingDocumentType", "FILM")));
            long versionId = ((Number) created.get("versionId")).longValue();
            var edit = command(Map.of("versionNo", "V1", "drawingFileId", 56, "drawingDocumentType", "FILM"));
            domain.execute("editVersion", versionId, edit);
            assertEquals("FILM", db.queryForObject("SELECT document_type FROM sys_attachment_ref WHERE attachment_id=56", String.class));
            assertEquals(56L, db.queryForObject("SELECT drawing_file_id FROM print_artwork_version WHERE version_id=?", Long.class, versionId));
            assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM print_artwork_version", Integer.class));
            verify(attachments).unlink(anyLong());
        }
    }

    @Test
    void sameDrawingCanSaveTypeWithoutCreatingOrUnlinkingReference() {
        var source = database();
        var db = new JdbcTemplate(source);
        db.update("INSERT INTO print_artwork(artwork_no,name,family) VALUES ('SAME','同图纸','面板')");
        db.update("INSERT INTO print_artwork_version(artwork_id,version_no,status,drawing_file_id) VALUES (1,'V1','DRAFT',55)");
        db.update("INSERT INTO sys_attachment_ref(attachment_id,source_type,source_id) VALUES (55,'print_artwork_version',1)");
        var attachments = mock(AttachmentRefService.class);
        when(attachments.batchUpdate(any())).thenAnswer(call -> {
            com.jjx.system.domain.dto.AttachmentRefBatchDTO batch = call.getArgument(0);
            var item = batch.getItems().getFirst();
            return db.update("UPDATE sys_attachment_ref SET document_type=? WHERE id=?", item.getDocumentType(), item.getRefId());
        });
        var domain = transactional(new ArtworkService(db, json, attachments), source);
        try (var user = mockStatic(SecurityUtils.class)) {
            user.when(SecurityUtils::getUsername).thenReturn("designer");
            domain.execute("editVersion", 1, command(Map.of("versionNo", "V1", "drawingFileId", 55, "drawingDocumentType", "FILM")));
            assertEquals("FILM", db.queryForObject("SELECT document_type FROM sys_attachment_ref WHERE attachment_id=55", String.class));
            assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM sys_attachment_ref", Integer.class));
            verify(attachments, never()).create(any());
            verify(attachments, never()).unlink(anyLong());
        }
    }

    @Test
    void optionalTypeFieldDoesNotChangeHistoricalRequestDigest() throws Exception {
        var source = database();
        var db = new JdbcTemplate(source);
        var service = service(source, mock(AttachmentRefService.class));
        var command = command(Map.of());
        try (var user = mockStatic(SecurityUtils.class)) {
            user.when(SecurityUtils::getUsername).thenReturn("designer");
            service.create(command);
            com.fasterxml.jackson.databind.node.ObjectNode legacy = json.valueToTree(command);
            legacy.remove(java.util.List.of("quantity", "dueDate", "drawingDocumentType"));
            var hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(json.writeValueAsBytes(java.util.List.of("ARTWORK", "createArtwork", 0, "designer", legacy))));
            assertEquals(hash, db.queryForObject("SELECT request_hash FROM engineering_resource_maintenance WHERE request_id=?", String.class, command.requestId()));
        }
    }

    @Test
    void attachmentFailureRollsBackMasterVersionProductRelationAndActionRecords() {
        var source = database();
        var db = new JdbcTemplate(source);
        var attachments = mock(AttachmentRefService.class);
        when(attachments.create(any())).thenAnswer(call -> {
            db.update("INSERT INTO sys_attachment_ref(attachment_id,source_type,source_id) VALUES (55,'print_artwork_version',1)");
            throw new BusinessException("图纸无权绑定");
        });
        var service = service(source, attachments);
        try (var user = mockStatic(SecurityUtils.class)) {
            user.when(SecurityUtils::getUsername).thenReturn("designer");
            assertThrows(BusinessException.class, () -> service.create(command(Map.of("drawingFileId", 55))));
            for (String table : java.util.List.of("print_artwork", "print_artwork_version",
                    "engineering_resource_product_rel", "engineering_resource_maintenance", "sys_attachment_ref")) {
                assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class), table);
            }
        }
    }

    @Test
    void mayCreateInitialDraftWithoutDrawing() {
        var source = database();
        var attachments = mock(AttachmentRefService.class);
        var service = service(source, attachments);
        try (var user = mockStatic(SecurityUtils.class)) {
            user.when(SecurityUtils::getUsername).thenReturn("designer");
            service.create(command(Map.of()));
            assertNull(new JdbcTemplate(source).queryForMap("SELECT drawing_file_id FROM print_artwork_version").get("drawing_file_id"));
            verifyNoInteractions(attachments);
        }
    }

    @Test
    void viewPermissionCannotUseCombinedCreationEndpoint() throws Exception {
        var original = cn.dev33.satoken.stp.StpUtil.getStpLogic();
        var logic = spy(new cn.dev33.satoken.stp.StpLogic("login"));
        doReturn(42L).when(logic).getLoginId();
        doReturn(java.util.List.of("engineering:film:view")).when(logic).getPermissionList(any());
        cn.dev33.satoken.stp.StpUtil.setStpLogic(logic);
        try {
            var service = mock(FilmCreationService.class);
            var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                    .standaloneSetup(new FilmCreationController(service))
                    .addInterceptors(new cn.dev33.satoken.interceptor.SaInterceptor()).build();
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .post("/engineering/printing/artworks/with-initial-version")
                    .contentType("application/json").content("{}"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
            verifyNoInteractions(service);
        } finally {
            cn.dev33.satoken.stp.StpUtil.setStpLogic(original);
        }
    }
}
