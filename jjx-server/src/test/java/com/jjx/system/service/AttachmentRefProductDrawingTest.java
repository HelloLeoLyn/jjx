package com.jjx.system.service;

import cn.dev33.satoken.stp.StpUtil;
import com.jjx.common.exception.BusinessException;
import com.jjx.system.domain.dto.AttachmentRefDTO;
import com.jjx.system.domain.entity.SysAttachment;
import com.jjx.system.domain.entity.SysAttachmentRef;
import com.jjx.system.mapper.SysAttachmentMapper;
import com.jjx.system.mapper.SysAttachmentRefMapper;
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
            + "released INT,upload_state VARCHAR(20),expires_at TIMESTAMP,deleted INT DEFAULT 0)");
        jdbc.execute("CREATE TABLE sys_attachment_ref(id BIGINT PRIMARY KEY,attachment_id BIGINT,source_type VARCHAR(32),"
            + "source_id BIGINT,source_no VARCHAR(64),product_id BIGINT,product_code VARCHAR(64),document_type VARCHAR(100),"
            + "display_name VARCHAR(255),remark VARCHAR(255),sort_order INT,update_time TIMESTAMP,deleted INT DEFAULT 0)");
        files = mock(SysAttachmentMapper.class);
        refs = mock(SysAttachmentRefMapper.class);
        service = new AttachmentRefService(files, refs, new AttachmentSourcePolicy(jdbc),
            mock(AttachmentAccessService.class), mock(AttachmentDocumentTypeService.class), jdbc);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (db != null) db.close();
    }

    private SysAttachment uploaded(Long id, String drawingNo, String version, String role, String category) {
        SysAttachment f = new SysAttachment();
        f.setId(id); f.setDrawingNo(drawingNo); f.setVersion(version); f.setFileRole(role); f.setCategory(category);
        f.setFileName("d.pdf"); f.setFileSize(10L); f.setFileType("application/pdf");
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
    void duplicateSameVersionRoleIsRejected() {
        seedSibling("DWG-1", "A", "ORIGINAL", "结构图", 0);
        bindable(uploaded(5L, "DWG-1", "A", "ORIGINAL", "结构图"));
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsString).thenReturn("1");
            BusinessException ex = assertThrows(BusinessException.class, () -> service.create(body(5L)));
            assertTrue(ex.getMessage().contains("相同用途"));
            verify(refs, never()).insert(any(SysAttachmentRef.class));
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
    void releasedSameVersionIsRejected() {
        seedSibling("DWG-1", "A", "PRINT", "结构图", 1);
        bindable(uploaded(5L, "DWG-1", "A", "ORIGINAL", "结构图"));
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsString).thenReturn("1");
            BusinessException ex = assertThrows(BusinessException.class, () -> service.create(body(5L)));
            assertTrue(ex.getMessage().contains("已下发"));
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
            assertEquals(1234L, r.getFileSize().longValue());
        }
    }
}
