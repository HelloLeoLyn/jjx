package com.jjx.system.service;

import cn.dev33.satoken.stp.StpUtil;
import com.jjx.common.exception.BusinessException;
import com.jjx.system.domain.entity.SysAttachment;
import com.jjx.system.enums.AttachmentUploadState;
import com.jjx.system.mapper.SysAttachmentMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 047 / 口径 A：到期且无引用的附件只允许「标记删除（进回收站）」，不做物理删除。
 * 断言三类边界：可清理的被标记；被引用/受控/历史/已回收的一律跳过；非 admin 拒绝。
 */
class AttachmentLifecycleRecycleTest {

    private static final long ID = 77L;

    private SysAttachment tempFile() {
        SysAttachment file = new SysAttachment();
        file.setId(ID);
        file.setFilePath("attachment-temp/x.bin");
        file.setUploadState(AttachmentUploadState.TEMP);
        file.setDeleted(false);
        file.setCreateTime(LocalDateTime.now().minusDays(9));
        file.setExpiresAt(LocalDateTime.now().minusDays(8));
        return file;
    }

    /** 到期、无有效引用、非受控 ⇒ 标记删除一次，结果 recycled=1 且明确 not physical。 */
    @Test
    void expiredUnreferencedTempIsMarkedDeleted() {
        SysAttachmentMapper files = mock(SysAttachmentMapper.class);
        AttachmentAccessService access = mock(AttachmentAccessService.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(contains("SELECT id FROM sys_attachment"), eq(Long.class), eq(0L), eq(100)))
                .thenReturn(List.of(ID));
        when(files.selectIncludingDeleted(ID)).thenReturn(tempFile());
        when(jdbc.queryForObject(contains("COUNT(*) FROM sys_attachment_ref"), eq(Long.class), eq(ID))).thenReturn(0L);
        when(access.protectedFile(any())).thenReturn(false);
        when(jdbc.queryForObject(contains("file_path=?"), eq(Long.class), any(), eq(ID))).thenReturn(0L);
        when(jdbc.query(contains("MAX(deleted_at)"), ArgumentMatchers.<RowMapper<LocalDateTime>>any(), eq(ID)))
                .thenReturn(List.of(LocalDateTime.now().minusDays(3)));
        when(files.logicalDelete(ID)).thenReturn(1);

        AttachmentLifecycleService service = new AttachmentLifecycleService(files, access, jdbc);
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsString).thenReturn("1");
            Map<String, Object> result = service.recycleEligible(0, 100);

            assertEquals(1, result.get("recycled"));
            assertEquals(1, result.get("scanned"));
            assertEquals(Boolean.FALSE, result.get("physicalDeleteEnabled"));
            verify(files, times(1)).logicalDelete(ID);
            verify(files, never()).restore(anyLong());
        }
    }

    /** 仍有有效引用 ⇒ 跳过（证明不会把在用附件清走）。 */
    @Test
    void activeReferenceIsSkipped() {
        SysAttachmentMapper files = mock(SysAttachmentMapper.class);
        AttachmentAccessService access = mock(AttachmentAccessService.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(contains("SELECT id FROM sys_attachment"), eq(Long.class), eq(0L), eq(100)))
                .thenReturn(List.of(ID));
        when(files.selectIncludingDeleted(ID)).thenReturn(tempFile());
        when(jdbc.queryForObject(contains("COUNT(*) FROM sys_attachment_ref"), eq(Long.class), eq(ID))).thenReturn(1L);

        AttachmentLifecycleService service = new AttachmentLifecycleService(files, access, jdbc);
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            Map<String, Object> result = service.recycleEligible(0, 100);

            assertEquals(0, result.get("recycled"));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> skipped = (List<Map<String, Object>>) result.get("skipped");
            assertEquals("ACTIVE_REFERENCE", skipped.get(0).get("reason"));
            verify(files, never()).logicalDelete(anyLong());
        }
    }

    /** 受控/历史引用（protectedFile）⇒ 跳过。 */
    @Test
    void controlledOrHistoricalIsSkipped() {
        SysAttachmentMapper files = mock(SysAttachmentMapper.class);
        AttachmentAccessService access = mock(AttachmentAccessService.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(contains("SELECT id FROM sys_attachment"), eq(Long.class), eq(0L), eq(100)))
                .thenReturn(List.of(ID));
        when(files.selectIncludingDeleted(ID)).thenReturn(tempFile());
        when(jdbc.queryForObject(contains("COUNT(*) FROM sys_attachment_ref"), eq(Long.class), eq(ID))).thenReturn(0L);
        when(access.protectedFile(any())).thenReturn(true);

        AttachmentLifecycleService service = new AttachmentLifecycleService(files, access, jdbc);
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            Map<String, Object> result = service.recycleEligible(0, 100);

            assertEquals(0, result.get("recycled"));
            verify(files, never()).logicalDelete(anyLong());
        }
    }

    /** 保留期未到（expires_at 在未来）⇒ 跳过，不提前清理。 */
    @Test
    void notYetExpiredIsSkipped() {
        SysAttachment future = tempFile();
        future.setCreateTime(LocalDateTime.now().minusDays(1));
        future.setExpiresAt(LocalDateTime.now().plusDays(6));

        SysAttachmentMapper files = mock(SysAttachmentMapper.class);
        AttachmentAccessService access = mock(AttachmentAccessService.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(contains("SELECT id FROM sys_attachment"), eq(Long.class), eq(0L), eq(100)))
                .thenReturn(List.of(ID));
        when(files.selectIncludingDeleted(ID)).thenReturn(future);
        when(jdbc.queryForObject(contains("COUNT(*) FROM sys_attachment_ref"), eq(Long.class), eq(ID))).thenReturn(0L);
        when(access.protectedFile(any())).thenReturn(false);
        when(jdbc.queryForObject(contains("file_path=?"), eq(Long.class), any(), eq(ID))).thenReturn(0L);
        when(jdbc.query(contains("MAX(deleted_at)"), ArgumentMatchers.<RowMapper<LocalDateTime>>any(), eq(ID)))
                .thenReturn(List.of(LocalDateTime.now().plusDays(6)));

        AttachmentLifecycleService service = new AttachmentLifecycleService(files, access, jdbc);
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            Map<String, Object> result = service.recycleEligible(0, 100);

            assertEquals(0, result.get("recycled"));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> skipped = (List<Map<String, Object>>) result.get("skipped");
            assertEquals("RETENTION_PERIOD", skipped.get(0).get("reason"));
            verify(files, never()).logicalDelete(anyLong());
        }
    }

    /** 已回收行不重复处理（幂等：重复点清理不会再报 recycled）。 */
    @Test
    void alreadyRecycledIsSkipped() {
        SysAttachment recycled = tempFile();
        recycled.setDeleted(true);

        SysAttachmentMapper files = mock(SysAttachmentMapper.class);
        AttachmentAccessService access = mock(AttachmentAccessService.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(contains("SELECT id FROM sys_attachment"), eq(Long.class), eq(0L), eq(100)))
                .thenReturn(List.of(ID));
        when(files.selectIncludingDeleted(ID)).thenReturn(recycled);

        AttachmentLifecycleService service = new AttachmentLifecycleService(files, access, jdbc);
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            Map<String, Object> result = service.recycleEligible(0, 100);

            assertEquals(0, result.get("recycled"));
            verify(files, never()).logicalDelete(anyLong());
        }
    }

    /** 非 admin 角色（预演/清理同权限）⇒ 直接拒绝。 */
    @Test
    void nonAdminIsRejected() {
        AttachmentLifecycleService service = new AttachmentLifecycleService(
                mock(SysAttachmentMapper.class), mock(AttachmentAccessService.class), mock(JdbcTemplate.class));
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            auth.when(() -> StpUtil.checkRole("admin")).thenThrow(new BusinessException("无权限"));
            assertThrows(BusinessException.class, () -> service.recycleEligible(0, 100));
        }
    }

    /** limit 越界拒绝，避免一次扫全表。 */
    @Test
    void limitIsGuarded() {
        AttachmentLifecycleService service = new AttachmentLifecycleService(
                mock(SysAttachmentMapper.class), mock(AttachmentAccessService.class), mock(JdbcTemplate.class));
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            assertThrows(BusinessException.class, () -> service.recycleEligible(0, 101));
            assertThrows(BusinessException.class, () -> service.recycleEligible(-1, 10));
        }
    }
}
