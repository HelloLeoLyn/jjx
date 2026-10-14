package com.jjx.system.service;

import cn.dev33.satoken.stp.StpUtil;
import com.jjx.common.exception.BusinessException;
import com.jjx.system.domain.entity.SysAttachment;
import com.jjx.system.mapper.SysAttachmentMapper;
import com.jjx.system.service.impl.SysAttachmentServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 047 / 口径 A 的配套修复：标记删除进回收站后必须能恢复。
 * 原实现按「业务来源」恢复，未绑定来源（TEMP/仅引用）的行一律拒绝 → 清理不可逆（2026-10-14 实测报错）。
 * 说明：只跑「恢复」分支，其余依赖（ProductMapper/JdbcTemplate/…）在两条分支里都不会被解引用，故传 null。
 */
class SysAttachmentRestoreTest {

    private static final long ID = 75L;

    private SysAttachment recycledRow(String bizType, Long bizId) {
        SysAttachment row = new SysAttachment();
        row.setId(ID);
        row.setDeleted(true);
        row.setBizType(bizType);
        row.setBizId(bizId);
        return row;
    }

    /** 无业务来源的回收站行（口径 A 清理的产物）⇒ 只回写 deleted 标记，可恢复。 */
    @Test
    void recycledRowWithoutBusinessSourceIsRestorable() {
        SysAttachmentMapper mapper = mock(SysAttachmentMapper.class);
        AttachmentSourcePolicy sources = mock(AttachmentSourcePolicy.class);
        when(mapper.selectRecycled()).thenReturn(List.of(recycledRow(null, null)));
        when(mapper.restore(ID)).thenReturn(1);

        SysAttachmentServiceImpl service = new SysAttachmentServiceImpl(
                mapper, null, null, null, null, null, null, sources, null, null);
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            assertTrue(service.restoreAttachment(ID));
        }
        verify(mapper, times(1)).restore(ID);
        verify(sources, never()).check(any(), any(), anyBoolean());
    }

    /** 有业务来源的行仍走原口径（来源权限校验不能被绕过）。 */
    @Test
    void recycledRowWithBusinessSourceStillChecksSourcePolicy() {
        SysAttachmentMapper mapper = mock(SysAttachmentMapper.class);
        AttachmentSourcePolicy sources = mock(AttachmentSourcePolicy.class);
        when(mapper.selectRecycled()).thenReturn(List.of(recycledRow("inquiry", 7L)));
        when(mapper.restore(ID)).thenReturn(1);

        SysAttachmentServiceImpl service = new SysAttachmentServiceImpl(
                mapper, null, null, null, null, null, null, sources, null, null);
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            assertTrue(service.restoreAttachment(ID));
        }
        verify(sources, times(1)).check("inquiry", 7L, true);
        verify(mapper, times(1)).restore(ID);
    }

    /** 不在回收站里的 id ⇒ 报错，不误恢复。 */
    @Test
    void restoreRejectsIdNotInRecycleBin() {
        SysAttachmentMapper mapper = mock(SysAttachmentMapper.class);
        when(mapper.selectRecycled()).thenReturn(List.of());

        SysAttachmentServiceImpl service = new SysAttachmentServiceImpl(
                mapper, null, null, null, null, null, null, mock(AttachmentSourcePolicy.class), null, null);
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            assertThrows(BusinessException.class, () -> service.restoreAttachment(ID));
        }
        verify(mapper, never()).restore(anyLong());
    }
}
