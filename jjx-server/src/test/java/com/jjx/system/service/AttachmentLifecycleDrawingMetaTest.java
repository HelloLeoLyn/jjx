package com.jjx.system.service;

import cn.dev33.satoken.stp.StpUtil;
import com.jjx.common.exception.BusinessException;
import com.jjx.system.domain.dto.AttachmentDrawingMetaDTO;
import com.jjx.system.domain.entity.SysAttachment;
import com.jjx.system.mapper.SysAttachmentMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 029（019 方案B）：公共 temp 上传可写 sys_attachment 既有图纸列。
 * 断言：① 传元数据 → 落列（编号/版本统一大写）；② 不传 → 不写这些列（行为不变）；
 * ③ 填了用途/名称却没填图纸编号 → 拒绝（同老口径）。
 */
class AttachmentLifecycleDrawingMetaTest {

    private static final String KEY = "abcdefghijklmnop"; // 16 位，匹配 requestKey 规则

    private SysAttachmentMapper files;
    private JdbcTemplate jdbc;

    private AttachmentLifecycleService newService(Path dir) throws Exception {
        files = mock(SysAttachmentMapper.class);
        AttachmentAccessService access = mock(AttachmentAccessService.class);
        jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), any(Object.class))).thenReturn(users());
        when(files.selectList(any())).thenReturn(List.of());
        AttachmentLifecycleService service = new AttachmentLifecycleService(files, access, jdbc);
        Field field = AttachmentLifecycleService.class.getDeclaredField("uploadBasePath");
        field.setAccessible(true);
        field.set(service, dir.toString());
        return service;
    }

    private List<Map<String, Object>> users() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(Map.of("user_id", "1"));
        return rows;
    }

    private MockMultipartFile file() {
        return new MockMultipartFile("file", "drawing.pdf", "application/pdf", "hello-pdf-bytes".getBytes());
    }

    private SysAttachment capturedInsert() {
        ArgumentCaptor<SysAttachment> captor = ArgumentCaptor.forClass(SysAttachment.class);
        verify(files).insert(captor.capture());
        return captor.getValue();
    }

    @Test
    void tempUploadWritesDrawingColumns(@TempDir Path dir) throws Exception {
        AttachmentLifecycleService service = newService(dir);
        AttachmentDrawingMetaDTO meta = new AttachmentDrawingMetaDTO();
        meta.setCategory("确认图");
        meta.setDrawingNo("dwg-001");
        meta.setDrawingName("面板图");
        meta.setVersion("b");
        meta.setFileRole("ORIGINAL");
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class);
             MockedStatic<TransactionSynchronizationManager> tx = mockStatic(TransactionSynchronizationManager.class)) {
            auth.when(StpUtil::getLoginIdAsString).thenReturn("1");
            service.upload(file(), KEY, meta);
            SysAttachment saved = capturedInsert();
            assertEquals("确认图", saved.getCategory());
            assertEquals("DWG-001", saved.getDrawingNo()); // 统一大写，避免同图纸分组被大小写拆开
            assertEquals("面板图", saved.getDrawingName());
            assertEquals("B", saved.getVersion());
            assertEquals("ORIGINAL", saved.getFileRole());
        }
    }

    @Test
    void tempUploadWithoutMetaLeavesDrawingColumnsNull(@TempDir Path dir) throws Exception {
        AttachmentLifecycleService service = newService(dir);
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class);
             MockedStatic<TransactionSynchronizationManager> tx = mockStatic(TransactionSynchronizationManager.class)) {
            auth.when(StpUtil::getLoginIdAsString).thenReturn("1");
            service.upload(file(), KEY, null);
            SysAttachment saved = capturedInsert();
            assertNull(saved.getCategory());
            assertNull(saved.getDrawingNo());
            assertNull(saved.getDrawingName());
            assertNull(saved.getVersion());
            assertNull(saved.getFileRole());
        }
    }

    @Test
    void roleWithoutDrawingNoIsRejected(@TempDir Path dir) throws Exception {
        AttachmentLifecycleService service = newService(dir);
        AttachmentDrawingMetaDTO meta = new AttachmentDrawingMetaDTO();
        meta.setFileRole("ORIGINAL");
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class);
             MockedStatic<TransactionSynchronizationManager> tx = mockStatic(TransactionSynchronizationManager.class)) {
            auth.when(StpUtil::getLoginIdAsString).thenReturn("1");
            assertThrows(BusinessException.class, () -> service.upload(file(), KEY, meta));
            verify(files, never()).insert(any(SysAttachment.class));
        }
    }

    @Test
    void invalidFileRoleIsRejected(@TempDir Path dir) throws Exception {
        AttachmentLifecycleService service = newService(dir);
        AttachmentDrawingMetaDTO meta = new AttachmentDrawingMetaDTO();
        meta.setDrawingNo("DWG-9");
        meta.setVersion("A");
        meta.setFileRole("SOMETHING");
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class);
             MockedStatic<TransactionSynchronizationManager> tx = mockStatic(TransactionSynchronizationManager.class)) {
            auth.when(StpUtil::getLoginIdAsString).thenReturn("1");
            assertThrows(BusinessException.class, () -> service.upload(file(), KEY, meta));
            verify(files, never()).insert(any(SysAttachment.class));
        }
    }
}
