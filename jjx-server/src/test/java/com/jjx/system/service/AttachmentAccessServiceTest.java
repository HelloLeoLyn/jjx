package com.jjx.system.service;

import com.jjx.common.exception.BusinessException;
import com.jjx.system.domain.entity.SysAttachment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 附件保留（dev-20261013-010）：被发布快照（DOCSET_FILE/DRAWING）引用的附件受保护，
 * 不可解除且不能越权；未受引用/受控标记之外的不误伤。
 */
@ExtendWith(MockitoExtension.class)
class AttachmentAccessServiceTest {

    @Mock JdbcTemplate jdbc;
    AttachmentAccessService access;

    @BeforeEach
    void setUp() {
        access = new AttachmentAccessService(null, null, jdbc);
    }

    private SysAttachment file() {
        SysAttachment file = new SysAttachment();
        file.setId(40L);
        file.setDeleted(false);
        file.setIsControlled(0);
        file.setIsCurrent(0);
        file.setReleased(0);
        return file;
    }

    @Test
    void snapshotFileReferenceProtectsAttachment() {
        when(jdbc.queryForObject(contains("product_work_spec_item"), eq(Long.class), any(), any(), any(), any()))
                .thenReturn(1L);
        assertTrue(access.protectedFile(file()));
    }

    @Test
    void protectedAttachmentCannotBeUnlinked() {
        when(jdbc.queryForObject(contains("product_work_spec_item"), eq(Long.class), any(), any(), any(), any()))
                .thenReturn(1L);
        assertThrows(BusinessException.class, () -> access.checkUnlink(file()));
    }

    @Test
    void unreferencedAttachmentStaysUnprotected() {
        when(jdbc.queryForObject(contains("product_work_spec_item"), eq(Long.class), any(), any(), any(), any()))
                .thenReturn(0L);
        when(jdbc.queryForObject(contains("review_flow"), eq(Long.class), any(), any(), any(), any(), any()))
                .thenReturn(0L);
        when(jdbc.queryForObject(contains("attachment_ref_ids"), eq(Long.class), eq(40L)))
                .thenReturn(0L);
        assertFalse(access.protectedFile(file()));
        assertDoesNotThrow(() -> access.checkUnlink(file()));
    }

    @Test
    void reviewedRefProtectsItsFile() {
        when(jdbc.queryForObject(contains("product_work_spec_item"), eq(Long.class), any(), any(), any(), any()))
                .thenReturn(0L);
        when(jdbc.queryForObject(contains("attachment_ref_ids"), eq(Long.class), eq(40L)))
                .thenReturn(1L);
        assertTrue(access.protectedFile(file()));
    }

    @Test
    void controlledOrReleasedShortCircuits() {
        SysAttachment released = file();
        released.setReleased(1);
        assertTrue(access.protectedFile(released));
    }
}
