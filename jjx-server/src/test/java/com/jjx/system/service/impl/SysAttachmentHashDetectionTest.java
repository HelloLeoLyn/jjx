package com.jjx.system.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.jjx.common.exception.BusinessException;
import com.jjx.system.domain.entity.SysAttachment;
import com.jjx.system.mapper.SysAttachmentMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SysAttachmentHashDetectionTest {
    private SysAttachmentMapper mapper;
    private SysAttachmentServiceImpl service;
    private static final String HASH = "a".repeat(64);

    @BeforeEach
    void setup() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), SysAttachment.class);
        mapper = mock(SysAttachmentMapper.class, withSettings().mockMaker(org.mockito.MockMakers.SUBCLASS));
        service = new SysAttachmentServiceImpl(mapper, null, null, null, null);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
    }

    @Test
    void requiresHashAndCompleteScopeBeforeQuerying() {
        assertThrows(BusinessException.class, () -> service.findBySha256("bad", "product", 1L, null));
        assertThrows(BusinessException.class, () -> service.findBySha256(HASH, null, 1L, null));
        assertThrows(BusinessException.class, () -> service.findBySha256(HASH, "product", null, null));
        assertThrows(BusinessException.class, () -> service.findBySha256(HASH, "product", 0L, null));
        verifyNoInteractions(mapper);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void bindsHashAndScopeAndExcludesUploadedRecord() {
        SysAttachment candidate = new SysAttachment();
        candidate.setId(8L);
        when(mapper.selectList(any())).thenReturn(List.of(candidate));
        assertEquals(List.of(candidate), service.findBySha256(HASH.toUpperCase(), "product", 2L, 9L));
        ArgumentCaptor<LambdaQueryWrapper<SysAttachment>> query = ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);
        verify(mapper).selectList(query.capture());
        String sql = query.getValue().getSqlSegment();
        assertTrue(sql.contains("sha256 ="));
        assertTrue(sql.contains("biz_type ="));
        assertTrue(sql.contains("biz_id ="));
        assertTrue(sql.contains("id <>"));
        assertTrue(query.getValue().getParamNameValuePairs().values().containsAll(List.of(HASH, "product", 2L, 9L)));
        assertTrue(TableInfoHelper.getTableInfo(SysAttachment.class).isWithLogicDelete());
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void missingHistoricalHashAndDetectionFailureDoNotFailUploadResult() {
        SysAttachment historical = new SysAttachment();
        when(mapper.selectById(1L)).thenReturn(historical);
        assertEquals(0, service.detectUploadedDuplicates(1L));
        when(mapper.selectById(2L)).thenThrow(new IllegalStateException("database unavailable"));
        assertEquals(0, service.detectUploadedDuplicates(2L));
        verify(mapper, never()).selectList(any());
    }
}
