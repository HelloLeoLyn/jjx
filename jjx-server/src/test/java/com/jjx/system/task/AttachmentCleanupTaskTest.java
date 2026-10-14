package com.jjx.system.task;

import com.jjx.system.config.AttachmentCleanupProperties;
import com.jjx.system.domain.entity.SysAttachment;
import com.jjx.system.domain.entity.SysOperLog;
import com.jjx.system.enums.AttachmentUploadState;
import com.jjx.system.mapper.SysAttachmentMapper;
import com.jjx.system.mapper.SysOperLogMapper;
import com.jjx.system.service.AttachmentAccessService;
import com.jjx.system.service.AttachmentLifecycleService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * dev-20261014-035（口径 b，只做①）：到期 TEMP 附件自动收口。
 * 覆盖：① 开关默认关 → 不执行；② 开 → 游标分页直到表尾 + 写一次运行汇总 oper_log；
 * ③ 自动入口（无人值守、无登录态）复用既有判定：到期才标记进回收站，未到期/已引用/受控一律不动，绝不删字节。
 */
class AttachmentCleanupTaskTest {

    private static final long ID = 77L;

    private SysAttachment tempFile() {
        SysAttachment file = new SysAttachment();
        file.setId(ID);
        file.setFileName("x.bin");
        file.setFileSize(3L);
        file.setFilePath("attachment-temp/x.bin");
        file.setSha256("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        file.setUploadState(AttachmentUploadState.TEMP);
        file.setDeleted(false);
        file.setCreateTime(LocalDateTime.now().minusDays(9));
        file.setExpiresAt(LocalDateTime.now().minusDays(8));
        return file;
    }

    /** 构造一个「到期、无引用、非受控、非共享」的清理场景（不含任何 StpUtil 登录态）。 */
    private AttachmentLifecycleService withEligibleRow(SysAttachment file, JdbcTemplate jdbc,
                                                       SysAttachmentMapper files, AttachmentAccessService access) {
        when(jdbc.queryForList(contains("SELECT id FROM sys_attachment"), eq(Long.class), eq(0L), eq(100)))
                .thenReturn(List.of(ID));
        when(files.selectIncludingDeleted(ID)).thenReturn(file);
        when(jdbc.queryForObject(contains("COUNT(*) FROM sys_attachment_ref"), eq(Long.class), eq(ID))).thenReturn(0L);
        when(access.protectedFile(any())).thenReturn(false);
        when(jdbc.queryForObject(contains("file_path=?"), eq(Long.class), any(), eq(ID))).thenReturn(0L);
        when(jdbc.query(contains("MAX(deleted_at)"), ArgumentMatchers.<RowMapper<LocalDateTime>>any(), eq(ID)))
                .thenReturn(List.of(LocalDateTime.now().minusDays(3)));
        return new AttachmentLifecycleService(files, access, jdbc, mock(SysOperLogMapper.class));
    }

    // ---- 开关（可关断） ----

    @Test
    void disabledTaskDoesNotRecycle() {
        AttachmentLifecycleService lifecycle = mock(AttachmentLifecycleService.class);
        AttachmentCleanupProperties props = new AttachmentCleanupProperties();
        props.setEnabled(false); // 默认值

        new AttachmentCleanupTask(lifecycle, props).autoRecycleExpiredTemp();

        verify(lifecycle, never()).autoRecycleExpired(anyLong(), anyInt());
        verify(lifecycle, never()).recordAutoCleanupRun(anyInt(), anyInt());
    }

    @Test
    void enabledTaskPaginatesUntilShortBatchAndWritesSummary() {
        AttachmentCleanupProperties props = new AttachmentCleanupProperties();
        props.setEnabled(true);
        props.setBatchSize(999); // 应被夹到 100

        AttachmentLifecycleService lifecycle = mock(AttachmentLifecycleService.class);
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("scanned", 100); first.put("recycled", 40); first.put("nextAfterId", 1000L);
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("scanned", 30); second.put("recycled", 5); second.put("nextAfterId", 1030L);
        when(lifecycle.autoRecycleExpired(0L, 100)).thenReturn(first);
        when(lifecycle.autoRecycleExpired(1000L, 100)).thenReturn(second);

        new AttachmentCleanupTask(lifecycle, props).autoRecycleExpiredTemp();

        verify(lifecycle).autoRecycleExpired(0L, 100);      // 尺寸夹到上限
        verify(lifecycle).autoRecycleExpired(1000L, 100);   // 游标推进
        verify(lifecycle, times(2)).autoRecycleExpired(anyLong(), anyInt());
        verify(lifecycle).recordAutoCleanupRun(130, 45);    // 汇总一次
    }

    // ---- 自动入口的判定（到期才标记、未到期不动、不碰已引用/受控） ----

    @Test
    void autoPathMarksExpiredTempWithoutLogin() {
        SysAttachmentMapper files = mock(SysAttachmentMapper.class);
        AttachmentAccessService access = mock(AttachmentAccessService.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AttachmentLifecycleService service = withEligibleRow(tempFile(), jdbc, files, access);
        when(files.logicalDelete(ID)).thenReturn(1);

        Map<String, Object> result = service.autoRecycleExpired(0, 100); // 全程无 StpUtil mock

        assertEquals(1, result.get("recycled"));
        assertEquals(1, result.get("scanned"));
        assertEquals(Boolean.FALSE, result.get("physicalDeleteEnabled"));
        assertEquals(Long.valueOf(ID), result.get("nextAfterId")); // 游标=末行 id
        verify(files, times(1)).logicalDelete(ID);
        // 红线：自动路径绝不删字节。
        verify(jdbc, never()).update(contains("PURGED"), anyLong());
    }

    @Test
    void autoPathSkipsNotYetExpired() {
        SysAttachment future = tempFile();
        future.setCreateTime(LocalDateTime.now().minusDays(1));
        future.setExpiresAt(LocalDateTime.now().plusDays(6));

        SysAttachmentMapper files = mock(SysAttachmentMapper.class);
        AttachmentAccessService access = mock(AttachmentAccessService.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AttachmentLifecycleService service = withEligibleRow(future, jdbc, files, access);
        when(jdbc.query(contains("MAX(deleted_at)"), ArgumentMatchers.<RowMapper<LocalDateTime>>any(), eq(ID)))
                .thenReturn(List.of(LocalDateTime.now().plusDays(6)));

        Map<String, Object> result = service.autoRecycleExpired(0, 100);

        assertEquals(0, result.get("recycled"));
        verify(files, never()).logicalDelete(anyLong());
    }

    @Test
    void autoPathSkipsActiveReference() {
        SysAttachmentMapper files = mock(SysAttachmentMapper.class);
        AttachmentAccessService access = mock(AttachmentAccessService.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(contains("SELECT id FROM sys_attachment"), eq(Long.class), eq(0L), eq(100)))
                .thenReturn(List.of(ID));
        when(files.selectIncludingDeleted(ID)).thenReturn(tempFile());
        when(jdbc.queryForObject(contains("COUNT(*) FROM sys_attachment_ref"), eq(Long.class), eq(ID))).thenReturn(1L);
        AttachmentLifecycleService service = new AttachmentLifecycleService(files, access, jdbc, mock(SysOperLogMapper.class));

        Map<String, Object> result = service.autoRecycleExpired(0, 100);

        assertEquals(0, result.get("recycled"));
        verify(files, never()).logicalDelete(anyLong());
    }

    @Test
    void autoPathSkipsControlled() {
        SysAttachmentMapper files = mock(SysAttachmentMapper.class);
        AttachmentAccessService access = mock(AttachmentAccessService.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(contains("SELECT id FROM sys_attachment"), eq(Long.class), eq(0L), eq(100)))
                .thenReturn(List.of(ID));
        when(files.selectIncludingDeleted(ID)).thenReturn(tempFile());
        when(jdbc.queryForObject(contains("COUNT(*) FROM sys_attachment_ref"), eq(Long.class), eq(ID))).thenReturn(0L);
        when(access.protectedFile(any())).thenReturn(true);
        AttachmentLifecycleService service = new AttachmentLifecycleService(files, access, jdbc, mock(SysOperLogMapper.class));

        Map<String, Object> result = service.autoRecycleExpired(0, 100);

        assertEquals(0, result.get("recycled"));
        verify(files, never()).logicalDelete(anyLong());
    }

    @Test
    void recordAutoCleanupRunWritesOperLog() {
        SysOperLogMapper operLogs = mock(SysOperLogMapper.class);
        AttachmentLifecycleService service = new AttachmentLifecycleService(
                mock(SysAttachmentMapper.class), mock(AttachmentAccessService.class), mock(JdbcTemplate.class), operLogs);

        service.recordAutoCleanupRun(20, 3);

        ArgumentCaptor<SysOperLog> captor = ArgumentCaptor.forClass(SysOperLog.class);
        verify(operLogs, times(1)).insert(captor.capture());
        SysOperLog entry = captor.getValue();
        assertEquals("sys_attachment", entry.getBizType());
        assertEquals("自动清理到期临时附件（移入回收站，不删字节）", entry.getAction());
        assertNotNull(entry.getDetail());
        assertTrue(entry.getDetail().contains("\"recycled\":3"));
        assertTrue(entry.getDetail().contains("\"physicalDelete\":false"));
    }
}
