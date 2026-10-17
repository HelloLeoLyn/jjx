package com.jjx.system.service;

import cn.dev33.satoken.stp.StpUtil;
import com.jjx.system.domain.dto.AttachmentRefBatchDTO;
import com.jjx.system.domain.dto.AttachmentRefBatchItem;
import com.jjx.system.domain.dto.AttachmentRefDTO;
import com.jjx.system.domain.dto.AttachmentRefMetadataDTO;
import com.jjx.system.domain.entity.SysAttachment;
import com.jjx.system.domain.entity.SysAttachmentRef;
import com.jjx.system.enums.AttachmentUploadState;
import com.jjx.system.mapper.SysAttachmentMapper;
import com.jjx.system.mapper.SysAttachmentRefMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AttachmentRefDisplayNameTest {
    private SysAttachmentMapper files;
    private SysAttachmentRefMapper refs;
    private AttachmentSourcePolicy sources;
    private AttachmentAccessService access;
    private AttachmentDocumentTypeService types;
    private AttachmentDisplayNameService names;
    private JdbcTemplate jdbc;
    private AttachmentRefOperationLogRecorder operationLogs;
    private AttachmentRefService service;

    @BeforeEach
    void setUp() {
        files = mock(SysAttachmentMapper.class);
        refs = mock(SysAttachmentRefMapper.class);
        sources = mock(AttachmentSourcePolicy.class);
        access = mock(AttachmentAccessService.class);
        types = mock(AttachmentDocumentTypeService.class);
        names = mock(AttachmentDisplayNameService.class);
        jdbc = mock(JdbcTemplate.class);
        operationLogs = mock(AttachmentRefOperationLogRecorder.class);
        service = new AttachmentRefService(files, refs, sources, access, types, names, jdbc);
        ReflectionTestUtils.setField(service, "operationLogs", operationLogs);
        when(sources.canonical("quotation")).thenReturn("quotation");
        when(sources.check(eq("quotation"), anyLong(), anyBoolean())).thenReturn("QT-3");
        when(sources.traceIdOf(anyString(), anyLong())).thenReturn(null);
        when(files.selectActiveForUpdate(anyLong())).thenAnswer(inv -> file(inv.getArgument(0)));
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
    }

    @Test
    void createSerializesGroupAndNumbersNewAutomaticName() {
        SysAttachment file = file(50L);
        when(files.selectActiveForUpdate(50L)).thenReturn(file);
        when(refs.lockActive(50L)).thenReturn(new ArrayList<>());
        when(refs.selectOne(any())).thenReturn(null);
        doAnswer(inv -> {
            ((SysAttachmentRef) inv.getArgument(0)).setId(501L);
            return 1;
        }).when(refs).insert(any(SysAttachmentRef.class));
        AttachmentRefDTO request = new AttachmentRefDTO();
        request.setAttachmentId(50L);
        request.setSourceType("quotation");
        request.setSourceId(3L);
        request.setProductId(7L);
        request.setDocumentType("CUSTOMER_DRAWING");

        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsString).thenReturn("operator");
            assertEquals(501L, service.create(request));
        }

        verify(names).lockGroups(List.of(new AttachmentDisplayNameService.GroupKey("quotation", 3L, 7L, "CUSTOMER_DRAWING")));
        verify(names).renumber("quotation", 3L, 7L, "CUSTOMER_DRAWING", "operator");
        ArgumentCaptor<SysAttachmentRef> created = ArgumentCaptor.forClass(SysAttachmentRef.class);
        verify(refs).insert(created.capture());
        assertNull(created.getValue().getDisplayName(), "自动名称由插入后按真实引用ID统一分配");
        verify(operationLogs).record(eq("quotation"), eq(3L), isNull(), eq(1), eq("附件新增"),
            eq(501L), eq(50L), eq("附件50"), eq(true), argThat(changes -> changes.get(0).contains("产品 ID 7")));
    }

    @Test
    void idempotentCreateDoesNotRecordAnotherAttachmentEvent() {
        SysAttachment file = file(50L);
        SysAttachmentRef existing = ref(501L, 50L, "CUSTOMER_DRAWING", "客户图纸");
        when(files.selectActiveForUpdate(50L)).thenReturn(file);
        when(refs.lockActive(50L)).thenReturn(List.of(existing));
        AttachmentRefDTO request = new AttachmentRefDTO();
        request.setAttachmentId(50L);
        request.setSourceType("quotation");
        request.setSourceId(3L);
        request.setProductId(7L);

        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsString).thenReturn("operator");
            assertEquals(501L, service.create(request));
        }

        verifyNoInteractions(operationLogs);
    }

    @Test
    void typeChangeRecomputesGeneratedNamesAndKeepsCustomNames() {
        SysAttachmentRef automatic = ref(5L, 50L, "CUSTOMER_DRAWING", "QT-3-P7-CUS-CD-客户图纸-01");
        SysAttachmentRef custom = ref(6L, 60L, "CUSTOMER_DRAWING", "客户确认版.pdf");
        when(refs.selectById(5L)).thenReturn(automatic);
        when(refs.selectById(6L)).thenReturn(custom);
        when(refs.lockActive(50L)).thenReturn(List.of(automatic));
        when(refs.lockActive(60L)).thenReturn(List.of(custom));
        when(names.baseName("quotation", 3L, 7L, "CUSTOMER_DRAWING")).thenReturn("QT-3-P7-CUS-CD-客户图纸");
        when(names.isGeneratedName(automatic.getDisplayName(), "QT-3-P7-CUS-CD-客户图纸")).thenReturn(true);
        when(names.isGeneratedName(custom.getDisplayName(), "QT-3-P7-CUS-CD-客户图纸")).thenReturn(false);
        AttachmentRefBatchItem autoItem = item(5L, "OTHER");
        AttachmentRefBatchItem customItem = item(6L, "OTHER");
        AttachmentRefBatchDTO body = new AttachmentRefBatchDTO();
        body.setItems(List.of(autoItem, customItem));

        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsString).thenReturn("operator");
            assertEquals(2, service.batchUpdate(body));
        }

        ArgumentCaptor<Object[]> writes = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc, times(2)).update(startsWith("UPDATE sys_attachment_ref SET document_type"), writes.capture());
        List<Object[]> values = writes.getAllValues();
        assertEquals("OTHER", values.get(0)[0]);
        assertNull(values.get(0)[1], "原自动名称不应残留在新资料类型组");
        assertEquals(5L, values.get(0)[5]);
        assertEquals("OTHER", values.get(1)[0]);
        assertEquals("客户确认版.pdf", values.get(1)[1], "自定义名称随类型变更保留");
        assertEquals(6L, values.get(1)[5]);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<AttachmentDisplayNameService.GroupKey>> groups = ArgumentCaptor.forClass(Collection.class);
        verify(names).lockGroups(groups.capture());
        assertTrue(groups.getValue().contains(new AttachmentDisplayNameService.GroupKey("quotation", 3L, 7L, "CUSTOMER_DRAWING")));
        assertTrue(groups.getValue().contains(new AttachmentDisplayNameService.GroupKey("quotation", 3L, 7L, "OTHER")));
        verify(names).renumber("quotation", 3L, 7L, "CUSTOMER_DRAWING", "operator");
        verify(names).renumber("quotation", 3L, 7L, "OTHER", "operator");
    }

    @Test
    void unlinkRenumbersRemainingGroupWithoutTouchingOtherGroups() {
        SysAttachmentRef ref = ref(5L, 50L, "CUSTOMER_DRAWING", "QT-3-P7-CUS-CD-客户图纸-02");
        when(refs.includingDeleted(5L)).thenReturn(ref);
        when(refs.lockActive(50L)).thenReturn(List.of(ref, ref(6L, 60L, "CUSTOMER_DRAWING", "other")));

        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsString).thenReturn("operator");
            service.unlink(5L);
        }

        verify(names).lockGroups(List.of(new AttachmentDisplayNameService.GroupKey("quotation", 3L, 7L, "CUSTOMER_DRAWING")));
        verify(names).renumber("quotation", 3L, 7L, "CUSTOMER_DRAWING", "operator");
    }

    @Test
    void unchangedMetadataDoesNotWriteOrLogAnOperation() {
        SysAttachmentRef ref = ref(5L, 50L, "CUSTOMER_DRAWING", "客户确认版.pdf");
        when(refs.selectById(5L)).thenReturn(ref);
        when(refs.lockActive(50L)).thenReturn(List.of(ref));
        when(names.baseName("quotation", 3L, 7L, "CUSTOMER_DRAWING")).thenReturn("generated");
        when(names.isGeneratedName(ref.getDisplayName(), "generated")).thenReturn(false);
        AttachmentRefMetadataDTO body = new AttachmentRefMetadataDTO();
        body.setDocumentType("CUSTOMER_DRAWING");
        body.setSortOrder(0);

        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsString).thenReturn("operator");
            service.update(5L, body);
        }

        verify(jdbc, never()).update(startsWith("UPDATE sys_attachment_ref SET document_type"), any(Object[].class));
        verifyNoInteractions(operationLogs);
    }

    @Test
    void metadataChangeRecordsTheOwningDocumentAndAttachment() {
        SysAttachmentRef ref = ref(5L, 50L, "CUSTOMER_DRAWING", "客户确认版.pdf");
        when(refs.selectById(5L)).thenReturn(ref);
        when(refs.lockActive(50L)).thenReturn(List.of(ref));
        when(names.baseName("quotation", 3L, 7L, "CUSTOMER_DRAWING")).thenReturn("generated");
        when(names.isGeneratedName(ref.getDisplayName(), "generated")).thenReturn(false);
        when(files.selectById(50L)).thenReturn(file(50L));
        AttachmentRefMetadataDTO body = new AttachmentRefMetadataDTO();
        body.setDocumentType("OTHER");
        body.setSortOrder(0);

        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsString).thenReturn("operator");
            service.update(5L, body);
        }

        verify(operationLogs).record(eq("quotation"), eq(3L), isNull(), eq(2), eq("附件资料修改"),
            eq(5L), eq(50L), anyString(), eq(true), argThat(changes -> changes.size() == 1 && changes.get(0).contains("资料类型")));
    }

    @Test
    void repeatedUnlinkDoesNotWriteOrLogAgain() {
        SysAttachmentRef ref = ref(5L, 50L, "CUSTOMER_DRAWING", "客户确认版.pdf");
        when(refs.includingDeleted(5L)).thenReturn(ref);
        when(refs.lockActive(50L)).thenReturn(List.of());

        service.unlink(5L);

        verifyNoInteractions(operationLogs);
        verify(jdbc, never()).update(startsWith("UPDATE sys_attachment_ref SET deleted=1"), any(Object[].class));
    }

    @Test
    void draftMetadataAndRemovalDoNotEnterBusinessTimeline() {
        SysAttachmentRef draft = ref(5L, 50L, "CUSTOMER_DRAWING", "草稿附件");
        draft.setRefState("draft");
        draft.setCreateBy("operator");
        when(refs.selectById(5L)).thenReturn(draft);

        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::getLoginIdAsString).thenReturn("operator");
            service.updateDraft(5L, "OTHER");
            service.removeDraft(5L);
        }

        verifyNoInteractions(operationLogs);
    }

    private AttachmentRefBatchItem item(Long id, String type) {
        AttachmentRefBatchItem item = new AttachmentRefBatchItem();
        item.setRefId(id);
        item.setDocumentType(type);
        return item;
    }

    private SysAttachmentRef ref(Long id, Long attachmentId, String type, String displayName) {
        SysAttachmentRef ref = new SysAttachmentRef();
        ref.setId(id);
        ref.setAttachmentId(attachmentId);
        ref.setSourceType("quotation");
        ref.setSourceId(3L);
        ref.setProductId(7L);
        ref.setDocumentType(type);
        ref.setDisplayName(displayName);
        ref.setSortOrder(0);
        return ref;
    }

    private SysAttachment file(Long id) {
        SysAttachment file = new SysAttachment();
        file.setId(id);
        file.setUploadState(AttachmentUploadState.TEMP);
        return file;
    }
}
