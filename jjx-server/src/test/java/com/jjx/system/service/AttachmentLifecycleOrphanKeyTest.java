package com.jjx.system.service;

import cn.dev33.satoken.stp.StpUtil;
import com.jjx.common.exception.BusinessException;
import com.jjx.system.domain.entity.SysAttachment;
import com.jjx.system.enums.AttachmentUploadState;
import com.jjx.system.mapper.SysAttachmentMapper;
import com.jjx.system.mapper.SysOperLogMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * dev-20261022-015：上传幂等命中"不可读旧附件"（解绑遗留 BOUND / owner 为空 / 已过期）时，
 * 不再抛"无权查看附件或历史来源尚未确认"，而是让位新建。
 */
class AttachmentLifecycleOrphanKeyTest {

    private static final String LOGIN_ID = "1";
    private static final String REQUEST_KEY = "abcdef0123456789";

    @TempDir
    Path tempDir;

    private String sha256(String content) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(md.digest(content.getBytes()));
    }

    private AttachmentLifecycleService service(SysAttachmentMapper files,
                                               AttachmentAccessService access,
                                               JdbcTemplate jdbc) {
        AttachmentLifecycleService svc = new AttachmentLifecycleService(files, access, jdbc,
                mock(SysOperLogMapper.class), mock(AttachmentSourcePolicy.class));
        ReflectionTestUtils.setField(svc, "uploadBasePath", tempDir.toString());
        return svc;
    }

    private SysAttachment orphan(String hash) throws Exception {
        SysAttachment old = new SysAttachment();
        old.setId(270L);
        old.setUploadKey(sha256(LOGIN_ID + ":" + REQUEST_KEY));
        old.setSha256(hash);
        old.setUploadState(AttachmentUploadState.BOUND);
        old.setDeleted(false);
        return old;
    }

    /** 旧行不可读 → 释放旧 upload_key 并新建一行；旧行本身不被删。 */
    @Test
    void unreadableOldRow_yieldsNewAttachment() throws Exception {
        String content = "image-bytes";
        String hash = sha256(content);
        SysAttachmentMapper files = mock(SysAttachmentMapper.class);
        AttachmentAccessService access = mock(AttachmentAccessService.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(java.util.Map.of("user_id", LOGIN_ID)));

        when(files.selectList(any())).thenReturn(List.of(orphan(hash)));
        doThrow(new BusinessException("无权查看附件或历史来源尚未确认")).when(access).checkRead(any());
        when(files.insert(any(SysAttachment.class))).thenAnswer(inv -> {
            ((SysAttachment) inv.getArgument(0)).setId(999L);
            return 1;
        });

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn(LOGIN_ID);
            MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", content.getBytes());
            Long id;
            org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
            try {
                id = service(files, access, jdbc).upload(file, REQUEST_KEY);
            } finally {
                org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
            }

            assertEquals(999L, id, "应返回新建附件 id");
            verify(jdbc).update(contains("SET upload_key=NULL"), eq(270L), anyString());
            verify(files).insert(any(SysAttachment.class));
        }
    }

    /** 旧行可读且内容一致 → 仍复用旧 id，不新建。 */
    @Test
    void readableSameContent_reusesOldRow() throws Exception {
        String content = "image-bytes";
        String hash = sha256(content);
        SysAttachmentMapper files = mock(SysAttachmentMapper.class);
        AttachmentAccessService access = mock(AttachmentAccessService.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(java.util.Map.of("user_id", LOGIN_ID)));

        when(files.selectList(any())).thenReturn(List.of(orphan(hash)));
        doNothing().when(access).checkRead(any());

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn(LOGIN_ID);
            MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", content.getBytes());
            Long id = service(files, access, jdbc).upload(file, REQUEST_KEY);

            assertEquals(270L, id, "可读且同内容应复用旧附件");
            verify(files, never()).insert(any(SysAttachment.class));
            verify(jdbc, never()).update(contains("upload_key=NULL"), any(), anyString());
        }
    }

    /** 旧行可读但内容不同 → 保持原有报错。 */
    @Test
    void readableDifferentContent_stillRejected() throws Exception {
        SysAttachmentMapper files = mock(SysAttachmentMapper.class);
        AttachmentAccessService access = mock(AttachmentAccessService.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(java.util.Map.of("user_id", LOGIN_ID)));

        when(files.selectList(any())).thenReturn(List.of(orphan("deadbeef")));
        doNothing().when(access).checkRead(any());

        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginIdAsString).thenReturn(LOGIN_ID);
            MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", "other".getBytes());
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service(files, access, jdbc).upload(file, REQUEST_KEY));
            assertTrue(ex.getMessage().contains("同一requestKey不能上传不同文件"));
            verify(files, never()).insert(any(SysAttachment.class));
        }
    }
}
