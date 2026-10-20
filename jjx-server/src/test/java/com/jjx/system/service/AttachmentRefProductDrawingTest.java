package com.jjx.system.service;

import cn.dev33.satoken.stp.StpUtil;
import com.jjx.common.exception.BusinessException;
import com.jjx.system.domain.dto.AttachmentRefDTO;
import com.jjx.system.domain.entity.SysAttachment;
import com.jjx.system.domain.entity.SysAttachmentRef;
import com.jjx.system.mapper.SysAttachmentMapper;
import com.jjx.system.mapper.SysAttachmentRefMapper;
import com.jjx.system.enums.AttachmentUploadState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * dev-20261014-016 第3项：工程图纸经公共链路（temp 上传写图纸列 + 建引用）落库后，
 * ① 产品图纸唯一性守卫按「引用表 source_type='product' AND product_id + 附件 drawing_no」判定（不依赖已废弃的 biz_type/biz_id）；
 * ② 按产品查引用的回填要带上 sys_attachment 图纸列（category/version/drawing_no/drawing_name/file_role/is_current/is_controlled/released）。
 */
public class AttachmentRefProductDrawingTest {

    private Connection db;
    private JdbcTemplate jdbc;
    private SysAttachmentMapper files;
    private SysAttachmentRefMapper refs;
    private AttachmentRefService service;

    @BeforeEach
    void setUp() throws Exception {
        db = DriverManager.getConnection("jdbc:h2:mem:prod_drawing;MODE=MySQL");
        jdbc = new JdbcTemplate(new SingleConnectionDataSource(db, true));
        jdbc.execute("CREATE TABLE product(product_id BIGINT PRIMARY KEY, product_code VARCHAR(64))");
        jdbc.execute("INSERT INTO product VALUES (1,'P1')");
        jdbc.execute("CREATE TABLE sys_attachment(id BIGINT PRIMARY KEY,biz_type VARCHAR(32),biz_id BIGINT,"
            + "file_name VARCHAR(255),file_size BIGINT,file_type VARCHAR(64),category VARCHAR(64),version VARCHAR(64),"
            + "drawing_no VARCHAR(64),drawing_name VARCHAR(128),file_role VARCHAR(20),is_current INT,is_controlled INT,"
            + "released INT,released_at TIMESTAMP,released_by VARCHAR(64),sha256 VARCHAR(64),upload_state VARCHAR(20),expires_at TIMESTAMP,deleted INT DEFAULT 0)");
        jdbc.execute("CREATE TABLE sys_attachment_ref(id BIGINT PRIMARY KEY,attachment_id BIGINT,source_type VARCHAR(32),"
            + "source_id BIGINT,source_no VARCHAR(64),product_id BIGINT,product_code VARCHAR(64),document_type VARCHAR(100),"
            + "display_name VARCHAR(255),remark VARCHAR(255),sort_order INT,update_time TIMESTAMP,deleted INT DEFAULT 0)");
        files = mock(SysAttachmentMapper.class);
        refs = mock(SysAttachmentRefMapper.class);
        service = new AttachmentRefService(files, refs, new AttachmentSourcePolicy(jdbc),
            mock(AttachmentAccessService.class), mock(AttachmentDocumentTypeService.class), mock(AttachmentDisplayNameService.class), jdbc);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (db != null) db.close();
    }

    private SysAttachment uploaded(Long id, String drawingNo, String version, String role, String category) {
        SysAttachment f = new SysAttachment();
        f.setId(id); f.setDrawingNo(drawingNo); f.setVersion(version); f.setFileRole(role); f.setCategory(category);
        f.setFileName("d.pdf"); f.setFileSize(10L); f.setFileType("application/pdf");
        f.setUploadState(AttachmentUploadState.TEMP);
        return f;
    }

    private void bindable(SysAttachment f) {
        when(files.selectActiveForUpdate(f.getId())).thenReturn(f);
        when(refs.lockActive(f.getId())).thenReturn(new ArrayList<>());
    }

    private AttachmentRefDTO body(Long attachmentId) {
        AttachmentRefDTO dto = new AttachmentRefDTO();
        dto.setAttachmentId(attachmentId); dto.setSourceType("product"); dto.setSourceId(1L); dto.setProductId(1L);
        return dto;
    }

    private void seedSibling(String drawingNo, String version, String role, String category, int released) {
        jdbc.update("INSERT INTO sys_attachment(id,file_name,category,version,drawing_no,file_role,released,deleted) VALUES (?,?,?,?,?,?,?,0)",
            99L, "old.pdf", category, version, drawingNo, role, released);
        jdbc.update("INSERT INTO sys_attachment_ref(id,attachment_id,source_type,source_id,product_id,deleted) VALUES (?,?,?,?,?,0)",
            900L, 99L, "product", 1L, 1L);
    }

    /** H2仅补齐MySQL JSON标量函数；业务服务、权限来源查询及所有引用写入使用真实JDBC。 */
    public static String jsonExtract(String value, String path) throws Exception {
        if (value == null) return null;
        var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(value).get(path.substring(2));
        return node == null || node.isNull() ? null : node.toString();
    }
    public static String jsonUnquote(String value) throws Exception {
        return value == null ? null : new com.fasterxml.jackson.databind.ObjectMapper().readTree(value).asText();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"product", "work_spec"})
    void realDraftDetachRetainsHistoricalReadAndCleanupProtectionAndRollsBack(String sourceType) throws Exception {
        jdbc.execute("ALTER TABLE product ADD work_spec_json VARCHAR(2000) DEFAULT '{}'");
        jdbc.execute("ALTER TABLE sys_attachment_ref ADD deleted_at TIMESTAMP");
        jdbc.execute("ALTER TABLE sys_attachment_ref ADD deleted_by VARCHAR(64)");
        jdbc.execute("ALTER TABLE sys_attachment_ref ADD update_by VARCHAR(64)");
        jdbc.execute("CREATE TABLE product_work_spec_version(id BIGINT PRIMARY KEY,product_id BIGINT,deleted INT)");
        jdbc.execute("CREATE TABLE product_work_spec_item(spec_version_id BIGINT,resource_type VARCHAR(30),source_id BIGINT)");
        jdbc.execute("CREATE TABLE quality_template_registry(file_id BIGINT,create_time TIMESTAMP)");
        jdbc.execute("CREATE TABLE engineering_film(file_id BIGINT)");
        jdbc.execute("ALTER TABLE sys_attachment ADD create_time TIMESTAMP");
        jdbc.execute("CREATE ALIAS JSON_EXTRACT FOR 'com.jjx.system.service.AttachmentRefProductDrawingTest.jsonExtract'");
        jdbc.execute("CREATE ALIAS JSON_UNQUOTE FOR 'com.jjx.system.service.AttachmentRefProductDrawingTest.jsonUnquote'");
        jdbc.update("INSERT INTO product VALUES (2,'P2','{}')");
        jdbc.update("INSERT INTO sys_attachment(id,file_name,sha256,upload_state,deleted) VALUES (5,'original.pdf','original-hash','BOUND',0)");
        jdbc.update("INSERT INTO sys_attachment_ref(id,attachment_id,source_type,source_id,product_id,deleted) VALUES (51,5,?,1,1,0),(52,5,?,2,2,0)", sourceType, sourceType);
        jdbc.update("INSERT INTO product_work_spec_version VALUES (10,1,0)");
        jdbc.update("INSERT INTO product_work_spec_item VALUES (10,'DOCSET_FILE',5)");
        when(files.selectActiveForUpdate(5L)).thenAnswer(call -> storedFixtureFile());
        when(files.selectIncludingDeleted(5L)).thenAnswer(call -> storedFixtureFile());
        when(refs.lockActive(5L)).thenAnswer(call -> jdbc.query("SELECT id,source_type,source_id FROM sys_attachment_ref WHERE attachment_id=5 AND deleted=0 FOR UPDATE", (rs,i) -> {
            var ref = new SysAttachmentRef(); ref.setId(rs.getLong(1)); ref.setSourceType(rs.getString(2)); ref.setSourceId(rs.getLong(3)); return ref;
        }));
        var policy = new AttachmentSourcePolicy(jdbc);
        var access = new AttachmentAccessService(policy, refs, jdbc);
        var lifecycle = new AttachmentLifecycleService(files, access, jdbc, mock(com.jjx.system.mapper.SysOperLogMapper.class), policy);
        var tx = new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource()));
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsString).thenReturn("other-user");
            auth.when(StpUtil::isLogin).thenReturn(true);
            auth.when(() -> StpUtil.hasPermission("production:order:view")).thenReturn(true);
            assertThrows(IllegalStateException.class, () -> tx.execute(status -> {
                service.detachWorkSpecFile(1L,5L); throw new IllegalStateException("later business/log failure");
            }));
            assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM sys_attachment_ref WHERE deleted=0", Integer.class));
            tx.executeWithoutResult(status -> service.detachWorkSpecFile(1L,5L));
            assertEquals(1, jdbc.queryForObject("SELECT deleted FROM sys_attachment_ref WHERE id=51", Integer.class));
            assertEquals(0, jdbc.queryForObject("SELECT deleted FROM sys_attachment_ref WHERE id=52", Integer.class));
            assertEquals("BOUND", storedFixtureFile().getUploadState().name());
            tx.executeWithoutResult(status -> service.detachWorkSpecFile(2L,5L));
            assertEquals(AttachmentUploadState.ORPHAN, storedFixtureFile().getUploadState());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM product_work_spec_item WHERE source_id=5", Integer.class));
            assertEquals("original-hash", jdbc.queryForObject("SELECT sha256 FROM sys_attachment WHERE id=5", String.class));
            assertTrue(access.canRead(storedFixtureFile()));
            assertTrue(access.protectedFile(storedFixtureFile()));
            assertThrows(BusinessException.class, () -> access.checkUnlink(storedFixtureFile()));
            assertEquals(0, lifecycle.autoRecycleExpired(0,100).get("recycled"));
            verify(files, never()).logicalDelete(anyLong());
        }
    }

    @Test void draftDetachRemovesBothSourcesOnlyForCurrentProductAndPreservesOtherBusiness() {
        jdbc.execute("ALTER TABLE sys_attachment_ref ADD deleted_at TIMESTAMP");
        jdbc.execute("ALTER TABLE sys_attachment_ref ADD deleted_by VARCHAR(64)");
        jdbc.execute("ALTER TABLE sys_attachment_ref ADD update_by VARCHAR(64)");
        jdbc.update("INSERT INTO sys_attachment(id,file_name,upload_state,deleted) VALUES (5,'shared.pdf','BOUND',0)");
        jdbc.update("INSERT INTO sys_attachment_ref(id,attachment_id,source_type,source_id,deleted) VALUES "
            + "(51,5,'product',1,0),(52,5,'work_spec',1,0),(53,5,'work_spec',2,0),(54,5,'quotation',1,0)");
        when(files.selectActiveForUpdate(5L)).thenAnswer(call -> storedFixtureFile());
        when(refs.lockActive(5L)).thenAnswer(call -> jdbc.query(
            "SELECT id,source_type,source_id FROM sys_attachment_ref WHERE attachment_id=5 AND deleted=0 FOR UPDATE", (rs,i) -> {
                var ref = new SysAttachmentRef(); ref.setId(rs.getLong(1)); ref.setSourceType(rs.getString(2)); ref.setSourceId(rs.getLong(3)); return ref;
            }));
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::isLogin).thenReturn(true);
            auth.when(StpUtil::getLoginIdAsString).thenReturn("editor");
            service.detachWorkSpecFile(1L, 5L);
            auth.verify(() -> StpUtil.checkPermission("product:edit"));
            assertEquals(List.of(53L, 54L), jdbc.queryForList("SELECT id FROM sys_attachment_ref WHERE deleted=0 ORDER BY id", Long.class));
            assertEquals(AttachmentUploadState.BOUND, storedFixtureFile().getUploadState());
            // Remaining other-business reference must also prevent orphaning after retry.
            service.detachWorkSpecFile(1L, 5L);
            assertEquals(AttachmentUploadState.BOUND, storedFixtureFile().getUploadState());
            jdbc.update("DELETE FROM sys_attachment_ref WHERE id IN (53,54)");
            jdbc.update("UPDATE sys_attachment_ref SET deleted=0 WHERE id IN (51,52)");
            service.detachWorkSpecFile(1L, 5L);
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM sys_attachment_ref WHERE deleted=0", Integer.class));
            assertEquals(AttachmentUploadState.ORPHAN, storedFixtureFile().getUploadState());
        }
    }

    private SysAttachment storedFixtureFile() {
        return jdbc.queryForObject("SELECT * FROM sys_attachment WHERE id=5", (rs,i) -> {
            var file = new SysAttachment(); file.setId(5L); file.setDeleted(false); file.setFileName(rs.getString("file_name"));
            file.setSha256(rs.getString("sha256")); file.setUploadState(AttachmentUploadState.valueOf(rs.getString("upload_state"))); return file;
        });
    }

    @Test
    void duplicateSameVersionRoleIsAllowed() {
        seedSibling("DWG-1", "A", "ORIGINAL", "结构图", 0);
        bindable(uploaded(5L, "DWG-1", "A", "ORIGINAL", "结构图"));
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsString).thenReturn("1");
            assertDoesNotThrow(() -> service.create(body(5L)));
            verify(refs).insert(any(SysAttachmentRef.class));
        }
    }

    @Test
    void categoryMismatchIsRejected() {
        seedSibling("DWG-1", "A", "ORIGINAL", "结构图", 0);
        bindable(uploaded(5L, "DWG-1", "B", "ORIGINAL", "面板图"));
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsString).thenReturn("1");
            BusinessException ex = assertThrows(BusinessException.class, () -> service.create(body(5L)));
            assertTrue(ex.getMessage().contains("文件类别"));
            verify(refs, never()).insert(any(SysAttachmentRef.class));
        }
    }

    @Test
    void releasedSameVersionAllowsSupplementWithoutChangingPublishedMembers() {
        seedSibling("DWG-1", "A", "PRINT", "结构图", 1);
        bindable(uploaded(5L, "DWG-1", "A", "ORIGINAL", "结构图"));
        jdbc.update("UPDATE sys_attachment SET is_current=1,released_at=TIMESTAMP '2026-10-19 10:00:00',released_by='original' WHERE id=99");
        jdbc.update("INSERT INTO sys_attachment(id,file_name,drawing_no,version,category,file_role,upload_state,deleted) VALUES (5,'new.pdf','DWG-1','A','结构图','ORIGINAL','TEMP',0)");
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsString).thenReturn("1");
            assertDoesNotThrow(() -> service.create(body(5L)));
            verify(refs).insert(any(SysAttachmentRef.class));
            assertEquals(1, jdbc.queryForObject("SELECT is_current FROM sys_attachment WHERE id=5", Integer.class));
            assertEquals(0, jdbc.queryForObject("SELECT released FROM sys_attachment WHERE id=5", Integer.class));
            assertEquals(1, jdbc.queryForObject("SELECT released FROM sys_attachment WHERE id=99", Integer.class));
            assertEquals("original", jdbc.queryForObject("SELECT released_by FROM sys_attachment WHERE id=99", String.class));
            assertEquals("2026-10-19 10:00:00.0", jdbc.queryForObject("SELECT released_at FROM sys_attachment WHERE id=99", java.sql.Timestamp.class).toString());
        }
    }

    @Test
    void supplementOfHistoricalVersionDoesNotTakeCurrentFromAnotherVersion() {
        seedSibling("DWG-1", "B", "PRINT", "结构图", 1);
        jdbc.update("UPDATE sys_attachment SET is_current=1 WHERE id=99");
        jdbc.update("INSERT INTO sys_attachment(id,file_name,deleted) VALUES (5,'history.pdf',0)");
        bindable(uploaded(5L, "DWG-1", "A", "PRINT", "结构图"));
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsString).thenReturn("1");
            service.create(body(5L));
            assertEquals(0, jdbc.queryForObject("SELECT is_current FROM sys_attachment WHERE id=5", Integer.class));
            assertEquals(1, jdbc.queryForObject("SELECT is_current FROM sys_attachment WHERE id=99", Integer.class));
        }
    }

    @Test
    void concurrentSupplementsSerializeOnProductUntilTransactionCompletes() throws Exception {
        var source = new org.springframework.jdbc.datasource.DriverManagerDataSource("jdbc:h2:mem:prod_drawing", "", "");
        var concurrentJdbc = new JdbcTemplate(source);
        var concurrentService = new AttachmentRefService(files, refs, new AttachmentSourcePolicy(concurrentJdbc),
            mock(AttachmentAccessService.class), mock(AttachmentDocumentTypeService.class), mock(AttachmentDisplayNameService.class), concurrentJdbc);
        var transaction = new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(source));
        var firstLocked = new java.util.concurrent.CountDownLatch(1);
        var finishFirst = new java.util.concurrent.CountDownLatch(1);
        when(files.selectActiveForUpdate(5L)).thenAnswer(call -> {
            firstLocked.countDown();
            assertTrue(finishFirst.await(5, java.util.concurrent.TimeUnit.SECONDS));
            return uploaded(5L, "DWG-1", "A", "PRINT", "结构图");
        });
        bindable(uploaded(6L, "DWG-1", "A", "PRINT", "结构图"));
        when(refs.lockActive(5L)).thenReturn(new ArrayList<>());
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.function.Consumer<Long> create = id -> {
            try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
                auth.when(StpUtil::getLoginIdAsString).thenReturn("1");
                transaction.executeWithoutResult(status -> concurrentService.create(body(id)));
            }
        };
        try {
            var first = pool.submit(() -> create.accept(5L));
            assertTrue(firstLocked.await(5, java.util.concurrent.TimeUnit.SECONDS));
            var second = pool.submit(() -> create.accept(6L));
            assertThrows(java.util.concurrent.TimeoutException.class, () -> second.get(150, java.util.concurrent.TimeUnit.MILLISECONDS));
            verify(files, never()).selectActiveForUpdate(6L);
            finishFirst.countDown();
            first.get(5, java.util.concurrent.TimeUnit.SECONDS);
            second.get(5, java.util.concurrent.TimeUnit.SECONDS);
            verify(files).selectActiveForUpdate(6L);
        } finally {
            finishFirst.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS));
        }
    }

    @Test
    void repeatedBindingReturnsExistingReferenceWithoutResettingPublication() {
        seedSibling("DWG-1", "A", "PRINT", "结构图", 1);
        SysAttachment f = uploaded(99L, "DWG-1", "A", "PRINT", "结构图");
        f.setUploadState(AttachmentUploadState.BOUND);
        bindable(f);
        SysAttachmentRef existing = new SysAttachmentRef();
        existing.setId(900L); existing.setAttachmentId(99L); existing.setSourceType("product");
        existing.setSourceId(1L); existing.setProductId(1L);
        when(refs.lockActive(99L)).thenReturn(List.of(existing));
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsString).thenReturn("1");
            assertEquals(900L, service.create(body(99L)));
            assertEquals(1, jdbc.queryForObject("SELECT released FROM sys_attachment WHERE id=99", Integer.class));
            verify(refs, never()).insert(any(SysAttachmentRef.class));
        }
    }

    @Test
    void differentRoleSameVersionIsAllowed() {
        seedSibling("DWG-1", "A", "ORIGINAL", "结构图", 0);
        bindable(uploaded(5L, "DWG-1", "A", "PRINT", "结构图"));
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsString).thenReturn("1");
            assertDoesNotThrow(() -> service.create(body(5L)));
            verify(refs, times(1)).insert(any(SysAttachmentRef.class));
        }
    }

    @Test
    void nonDrawingAttachmentSkipsGuard() {
        // 普通产品资料（无图纸编号）：即使同产品已有同版本同用途行也不触发图纸守卫。
        jdbc.update("INSERT INTO sys_attachment(id,file_name,category,version,drawing_no,file_role,deleted) VALUES (?,?,?,?,?,?,0)",
            99L, "spec.pdf", "客供稿", null, null, null);
        jdbc.update("INSERT INTO sys_attachment_ref(id,attachment_id,source_type,source_id,product_id,deleted) VALUES (?,?,?,?,?,0)",
            900L, 99L, "product", 1L, 1L);
        SysAttachment plain = new SysAttachment();
        plain.setId(5L); plain.setFileName("a.pdf"); plain.setCategory("客供稿");
        bindable(plain);
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsString).thenReturn("1");
            assertDoesNotThrow(() -> service.create(body(5L)));
            verify(refs, times(1)).insert(any(SysAttachmentRef.class));
        }
    }

    @Test
    void productQueryBackfillsDrawingColumns() {
        jdbc.update("INSERT INTO sys_attachment(id,file_name,file_size,file_type,category,version,drawing_no,drawing_name,file_role,is_current,is_controlled,released,deleted) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,0)",
            40L, "panel.pdf", 1234L, "application/pdf", "结构图", "B", "DWG-9", "面板图", "PRINT", 1, 1, 0);
        jdbc.update("UPDATE sys_attachment SET sha256=? WHERE id=40", "a".repeat(64));
        SysAttachmentRef row = new SysAttachmentRef();
        row.setId(90L); row.setAttachmentId(40L); row.setSourceType("product"); row.setSourceId(1L); row.setProductId(1L);
        row.setCreateBy("u"); row.setCreateTime(LocalDateTime.now());
        when(refs.selectList(any())).thenReturn(new ArrayList<>(List.of(row)));
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::isLogin).thenReturn(true);
            auth.when(() -> StpUtil.hasPermission(anyString())).thenReturn(true);
            List<SysAttachmentRef> out = service.product(1L);
            assertEquals(1, out.size());
            SysAttachmentRef r = out.get(0);
            assertEquals("DWG-9", r.getDrawingNo());
            assertEquals("面板图", r.getDrawingName());
            assertEquals("结构图", r.getCategory());
            assertEquals("B", r.getVersion());
            assertEquals("PRINT", r.getFileRole());
            assertEquals(1, r.getIsCurrent().intValue());
            assertEquals(1, r.getIsControlled().intValue());
            assertEquals(0, r.getReleased().intValue());
            assertEquals("panel.pdf", r.getFileName());
            assertEquals("a".repeat(64), r.getSha256());
            assertEquals(1234L, r.getFileSize().longValue());
        }
    }
}
