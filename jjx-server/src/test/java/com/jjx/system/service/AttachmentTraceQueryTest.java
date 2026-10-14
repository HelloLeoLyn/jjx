package com.jjx.system.service;

import com.jjx.system.domain.entity.SysAttachment;
import com.jjx.system.domain.entity.SysAttachmentRef;
import com.jjx.system.enums.AttachmentUploadState;
import com.jjx.system.mapper.SysAttachmentMapper;
import com.jjx.system.mapper.SysAttachmentRefMapper;
import com.jjx.system.service.impl.SysAttachmentServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttachmentTraceQueryTest {
    @Mock SysAttachmentMapper attachmentMapper;
    @Mock SysAttachmentRefMapper attachmentRefMapper;
    @Mock AttachmentAccessService attachmentAccess;
    @Mock AttachmentSourcePolicy attachmentSources;
    @Spy @InjectMocks SysAttachmentServiceImpl service;

    private SysAttachment file() {
        SysAttachment file = new SysAttachment();
        file.setId(40L);
        file.setFileName("图纸.pdf");
        file.setUploadState(AttachmentUploadState.BOUND);
        return file;
    }

    private SysAttachmentRef ref(long sourceId) {
        SysAttachmentRef ref = new SysAttachmentRef();
        ref.setAttachmentId(40L);
        ref.setSourceType("inquiry");
        ref.setSourceId(sourceId);
        return ref;
    }

    @Test
    void refsWorkWithoutLegacyFieldsAndDuplicateFilesAppearOnce() {
        when(attachmentRefMapper.selectByTrace("chain")).thenReturn(List.of(ref(1), ref(2)));
        when(attachmentSources.canRead("inquiry", 1L)).thenReturn(true);
        when(attachmentSources.check("inquiry", 1L, false)).thenReturn("IN001");
        when(attachmentMapper.selectById(40L)).thenReturn(file());
        doReturn(List.of(file())).when(service).getAttachmentsByTraceId("chain");
        var rows = service.getAttachmentSourcesByTraceId("chain");
        assertEquals(1, rows.size());
        assertEquals("IN001", rows.getFirst().getSourceNo());
        assertEquals("询价单", rows.getFirst().getBizTypeName());
        assertEquals("chain", rows.getFirst().getTraceId());
        verify(attachmentMapper, times(1)).selectById(40L);
    }

    @Test
    void unreadableSourceDoesNotLeakFileAndNextReadableRefCanBeUsed() {
        when(attachmentRefMapper.selectByTrace("chain")).thenReturn(List.of(ref(1), ref(2)));
        when(attachmentSources.canRead("inquiry", 1L)).thenReturn(false);
        when(attachmentSources.canRead("inquiry", 2L)).thenReturn(true);
        when(attachmentSources.check("inquiry", 2L, false)).thenReturn("IN002");
        when(attachmentMapper.selectById(40L)).thenReturn(file());
        doReturn(List.of()).when(service).getAttachmentsByTraceId("chain");
        assertEquals("IN002", service.getAttachmentSourcesByTraceId("chain").getFirst().getSourceNo());
    }

    @Test
    void noPermissionMeansNoRows() {
        when(attachmentRefMapper.selectByTrace("chain")).thenReturn(List.of(ref(1)));
        doReturn(List.of()).when(service).getAttachmentsByTraceId("chain");
        assertTrue(service.getAttachmentSourcesByTraceId("chain").isEmpty());
        verifyNoInteractions(attachmentMapper);
    }

    @Test
    void detachedReferencesCannotFallBackToStaleFileTrace() {
        when(attachmentRefMapper.selectByTrace("chain")).thenReturn(List.of());
        when(attachmentRefMapper.countHistory(40L)).thenReturn(1L);
        doReturn(List.of(file())).when(service).getAttachmentsByTraceId("chain");
        assertTrue(service.getAttachmentSourcesByTraceId("chain").isEmpty());
        verifyNoInteractions(attachmentAccess, attachmentSources);
    }

    @Test
    void legacyFileWithoutReferenceHistoryStillWorks() {
        SysAttachment legacy = file();
        legacy.setBizType("inquiry");
        legacy.setBizId(1L);
        when(attachmentRefMapper.selectByTrace("chain")).thenReturn(List.of());
        doReturn(List.of(legacy)).when(service).getAttachmentsByTraceId("chain");
        when(attachmentAccess.canRead(legacy)).thenReturn(true);
        when(attachmentSources.canRead("inquiry", 1L)).thenReturn(true);
        when(attachmentSources.check("inquiry", 1L, false)).thenReturn("IN001");
        assertEquals("IN001", service.getAttachmentSourcesByTraceId("chain").getFirst().getSourceNo());
    }

    @Test
    void deletedAndUnboundFilesAreExcluded() {
        when(attachmentRefMapper.selectByTrace("chain")).thenReturn(List.of(ref(1)));
        when(attachmentSources.canRead("inquiry", 1L)).thenReturn(true);
        doReturn(List.of()).when(service).getAttachmentsByTraceId("chain");
        SysAttachment file = file();
        when(attachmentMapper.selectById(40L)).thenReturn(file);
        file.setDeleted(true);
        assertTrue(service.getAttachmentSourcesByTraceId("chain").isEmpty());
        file.setDeleted(false);
        for (var state : List.of(AttachmentUploadState.TEMP, AttachmentUploadState.ORPHAN)) {
            file.setUploadState(state);
            assertTrue(service.getAttachmentSourcesByTraceId("chain").isEmpty());
        }
    }

    @Test
    void blankTraceDoesNotQueryAnything() {
        assertTrue(service.getAttachmentSourcesByTraceId(" ").isEmpty());
        verifyNoInteractions(attachmentRefMapper, attachmentMapper);
    }

    @Test
    void mapperUsesLiveSourceTraceAndExcludesDetachedOrDeletedSources() throws Exception {
        try (var db = java.sql.DriverManager.getConnection("jdbc:h2:mem:trace_query;MODE=MySQL")) {
            var sql = db.createStatement();
            sql.execute("CREATE TABLE sys_attachment_ref(id BIGINT, attachment_id BIGINT, source_type VARCHAR, source_id BIGINT, deleted INT, create_time TIMESTAMP)");
            sql.execute("CREATE TABLE sales_inquiry(inquiry_id BIGINT, trace_id VARCHAR, deleted INT)");
            sql.execute("CREATE TABLE sales_quotation(quotation_id BIGINT, trace_id VARCHAR, deleted INT)");
            sql.execute("CREATE TABLE sales_order(order_id BIGINT, trace_id VARCHAR, deleted INT)");
            sql.execute("CREATE TABLE sales_sample_order(order_id BIGINT, deleted INT)");
            sql.execute("CREATE TABLE purchase_order(order_id BIGINT, trace_id VARCHAR)");
            sql.execute("CREATE TABLE production_order(order_id BIGINT, trace_id VARCHAR)");
            sql.execute("INSERT INTO sales_inquiry VALUES (1,'chain',0),(2,'other',0),(3,'chain',1)");
            sql.execute("INSERT INTO sales_quotation VALUES (4,'chain',0)");
            sql.execute("INSERT INTO sys_attachment_ref VALUES "
                + "(1,40,'inquiry',1,0,NULL),(2,41,'inquiry',2,0,NULL),"
                + "(3,42,'inquiry',3,0,NULL),(4,43,'inquiry',1,1,NULL),"
                + "(5,44,'quotation',4,0,NULL),(6,45,'inquiry',99,0,NULL)");
            String query = SysAttachmentRefMapper.class.getMethod("selectByTrace", String.class)
                .getAnnotation(org.apache.ibatis.annotations.Select.class).value()[0]
                .replace("#{traceId}", "?");
            try (var statement = db.prepareStatement(query)) {
                for (int i = 1; i <= 6; i++) statement.setString(i, "chain");
                var rows = statement.executeQuery();
                var ids = new java.util.ArrayList<Long>();
                while (rows.next()) ids.add(rows.getLong("attachment_id"));
                assertEquals(List.of(40L, 44L), ids);
            }
        }
    }
}
