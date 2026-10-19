package com.jjx.system.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.jjx.common.exception.BusinessException;
import com.jjx.system.domain.entity.SysAttachment;
import com.jjx.system.mapper.SysAttachmentMapper;
import com.jjx.system.service.impl.SysAttachmentServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 验证整版下发只写新增成员；已下发成员不被重新打时间，撤回覆盖整版。 */
class DrawingVersionPublicationTest {
    private SysAttachment file(long id, int current, int released) {
        SysAttachment f = new SysAttachment();
        f.setId(id); f.setDrawingNo("P1-ST"); f.setVersion("A");
        f.setIsCurrent(current); f.setReleased(released);
        return f;
    }

    @SuppressWarnings("unchecked")
    private SysAttachmentServiceImpl service(List<SysAttachment> files) {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, "test"), SysAttachment.class);
        SysAttachmentMapper mapper = mock(SysAttachmentMapper.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(Long.class), any())).thenReturn(List.of(1L));
        when(mapper.selectById(anyLong())).thenReturn(files.get(0));
        when(mapper.selectActiveForUpdate(anyLong())).thenReturn(files.get(0));
        when(mapper.selectDrawingForUpdate(1L, "P1-ST")).thenReturn(files);
        SysAttachmentServiceImpl service = spy(new SysAttachmentServiceImpl(mapper, null, null, jdbc, null, null, null, null, null, null));
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        doReturn(true).when(service).update(any(LambdaUpdateWrapper.class));
        return service;
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishOnlyNewMembersWhileRecallTargetsWholeVersion() {
        SysAttachmentServiceImpl service = service(List.of(file(11, 1, 1), file(12, 1, 0)));
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginId).thenReturn(7L);
            assertTrue(service.setReleased(11L, true));
        }
        var updates = org.mockito.ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(service).update(updates.capture());
        var publish = updates.getValue();
        publish.getSqlSegment();
        assertTrue(publish.getParamNameValuePairs().containsValue(12L));
        assertFalse(publish.getParamNameValuePairs().containsValue(11L));
        clearInvocations(service);
        assertTrue(service.setReleased(11L, false));
        verify(service).update(updates.capture());
        var recall = updates.getValue();
        recall.getSqlSegment();
        assertTrue(recall.getParamNameValuePairs().containsValue(11L));
        assertTrue(recall.getParamNameValuePairs().containsValue(12L));
    }

    @Test
    void nonCurrentVersionCannotBePublishedAndAlreadyPublishedIsIdempotent() {
        var historical = service(List.of(file(11, 0, 0)));
        assertThrows(BusinessException.class, () -> historical.setReleased(11L, true));
        var published = service(List.of(file(11, 1, 1)));
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            assertTrue(published.setReleased(11L, true));
        }
        verify(published, never()).update(any(LambdaUpdateWrapper.class));
    }
}
