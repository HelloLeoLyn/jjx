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
class AttachmentRefProductDrawingTest {

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
