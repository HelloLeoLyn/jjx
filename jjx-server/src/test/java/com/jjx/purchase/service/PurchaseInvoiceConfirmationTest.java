package com.jjx.purchase.service;

import cn.dev33.satoken.stp.StpUtil;
import com.jjx.common.exception.BusinessException;
import com.jjx.event.EventPublisher;
import com.jjx.purchase.domain.entity.PurchaseDocument;
import com.jjx.purchase.mapper.PurchaseDocumentMapper;
import com.jjx.purchase.mapper.PurchaseOrderMapper;
import com.jjx.purchase.service.impl.PurchaseDocumentServiceImpl;
import com.jjx.system.domain.dto.AttachmentRefDTO;
import com.jjx.system.domain.entity.SysAttachment;
import com.jjx.system.enums.AttachmentUploadState;
import com.jjx.system.mapper.SysAttachmentMapper;
import com.jjx.system.service.AttachmentRefService;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 采购发票核销时随事务建立公共附件引用（dev-20261014-022，照收货票据 018 镜像）。
 */
class PurchaseInvoiceConfirmationTest {
    JdbcTemplate jdbc;
    IPurchaseDocumentService documents;
    PurchaseInvoiceAttachmentService attachments;
    AttachmentRefService refs;
    MockedStatic<StpUtil> auth;

    @BeforeEach void setup() {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE purchase_document(document_id BIGINT AUTO_INCREMENT PRIMARY KEY,document_no VARCHAR(50),document_type VARCHAR(20),document_status INT,file_url VARCHAR(500),remark VARCHAR(500),verification_date DATE,update_time TIMESTAMP)");
        jdbc.execute("CREATE TABLE sys_attachment(id BIGINT PRIMARY KEY,upload_state VARCHAR(20),owner VARCHAR(20),mime VARCHAR(80),size BIGINT,expires TIMESTAMP)");
        jdbc.execute("CREATE TABLE sys_attachment_ref(id BIGINT AUTO_INCREMENT PRIMARY KEY,attachment_id BIGINT,source_id BIGINT,source_type VARCHAR(50),deleted TINYINT DEFAULT 0)");
        jdbc.update("INSERT INTO purchase_document(document_id,document_no,document_type,document_status) VALUES(1,'INV-1','invoice',0)");
        file(100, "7", "application/pdf", 100, LocalDateTime.now().plusDays(1));
        file(101, "7", "image/png", 100, LocalDateTime.now().plusDays(1));
        auth = mockStatic(StpUtil.class);
        auth.when(StpUtil::getLoginIdAsString).thenReturn("7");

        SysAttachmentMapper files = mock(SysAttachmentMapper.class);
        when(files.selectActiveForUpdate(anyLong())).thenAnswer(call -> {
            var rows = jdbc.queryForList("SELECT * FROM sys_attachment WHERE id=?", call.getArgument(0, Long.class));
            if (rows.isEmpty()) return null;
            var r = rows.get(0); var f = new SysAttachment();
            f.setId(((Number) r.get("id")).longValue());
            f.setUploadState(AttachmentUploadState.valueOf((String) r.get("upload_state")));
            f.setUploadOwner((String) r.get("owner"));
            f.setFileType((String) r.get("mime"));
            f.setFileSize(((Number) r.get("size")).longValue());
            f.setExpiresAt(((java.sql.Timestamp) r.get("expires")).toLocalDateTime());
            return f;
        });
        refs = mock(AttachmentRefService.class);
        when(refs.create(any())).thenAnswer(call -> {
            AttachmentRefDTO r = call.getArgument(0);
            jdbc.update("INSERT INTO sys_attachment_ref(attachment_id,source_id,source_type) VALUES(?,?,?)",
                r.getAttachmentId(), r.getSourceId(), r.getSourceType());
            jdbc.update("UPDATE sys_attachment SET upload_state=? WHERE id=?", AttachmentUploadState.BOUND.name(), r.getAttachmentId());
            return 1L;
        });
        attachments = new PurchaseInvoiceAttachmentService(jdbc, files, refs);

        var documentMapper = mock(PurchaseDocumentMapper.class);
        when(documentMapper.selectById(anyLong())).thenAnswer(c -> {
            var rows = jdbc.queryForList("SELECT * FROM purchase_document WHERE document_id=?", c.getArgument(0, Long.class));
            if (rows.isEmpty()) return null;
            var r = rows.get(0); var d = new PurchaseDocument();
            d.setDocumentId(((Number) r.get("document_id")).longValue());
            d.setDocumentNo((String) r.get("document_no"));
            d.setDocumentType((String) r.get("document_type"));
            d.setDocumentStatus(((Number) r.get("document_status")).intValue());
            d.setFileUrl((String) r.get("file_url"));
            return d;
        });
        when(documentMapper.updateById(any(PurchaseDocument.class))).thenAnswer(c -> {
            var d = (PurchaseDocument) c.getArgument(0);
            return jdbc.update("UPDATE purchase_document SET document_status=? WHERE document_id=?", d.getDocumentStatus(), d.getDocumentId());
        });

        var service = mock(PurchaseDocumentServiceImpl.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(service, "documentMapper", documentMapper);
        ReflectionTestUtils.setField(service, "invoiceAttachments", attachments);
        ReflectionTestUtils.setField(service, "purchaseOrderMapper", mock(PurchaseOrderMapper.class));
        ReflectionTestUtils.setField(service, "eventPublisher", mock(EventPublisher.class));
        var proxy = new ProxyFactory(service);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(ds), new AnnotationTransactionAttributeSource()));
        documents = (IPurchaseDocumentService) proxy.getProxy();
    }

    @AfterEach void close() { auth.close(); }

    void file(long id, String owner, String mime, long size, LocalDateTime expiry) {
        jdbc.update("INSERT INTO sys_attachment VALUES(?,?,?,?,?,?)", id, AttachmentUploadState.TEMP.name(), owner, mime, size, expiry);
    }
    int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }

    @Test void verifyBindsTempAttachmentsInSameTransaction() {
        assertEquals(1, documents.verifyDocument(1L, "tester", "2026-10-14", "ok", List.of(100L)));
        assertEquals(1, jdbc.queryForObject("SELECT document_status FROM purchase_document WHERE document_id=1", Integer.class));
        assertEquals(1, count("sys_attachment_ref"));
        assertEquals("purchase_invoice", jdbc.queryForObject("SELECT source_type FROM sys_attachment_ref", String.class));
        assertEquals(1L, jdbc.queryForObject("SELECT source_id FROM sys_attachment_ref", Long.class));
        assertEquals(AttachmentUploadState.BOUND.name(), jdbc.queryForObject("SELECT upload_state FROM sys_attachment WHERE id=100", String.class));
    }

    @Test void verifyBindsCarriedAttachmentFromFileUrl() {
        jdbc.update("UPDATE purchase_document SET file_url='/system/attachment/download/101' WHERE document_id=1");
        assertEquals(1, documents.verifyDocument(1L, "tester", "2026-10-14", null));
        assertEquals(1, count("sys_attachment_ref"));
        assertEquals(101L, jdbc.queryForObject("SELECT attachment_id FROM sys_attachment_ref", Long.class));
    }

    @Test void bindFailureRollsBackStatusAndRefs() {
        doThrow(new BusinessException("绑定失败")).when(refs).create(any());
        assertThrows(BusinessException.class, () -> documents.verifyDocument(1L, "tester", "2026-10-14", null, List.of(100L)));
        assertEquals(0, jdbc.queryForObject("SELECT document_status FROM purchase_document WHERE document_id=1", Integer.class));
        assertEquals(0, count("sys_attachment_ref"));
        assertEquals(AttachmentUploadState.TEMP.name(), jdbc.queryForObject("SELECT upload_state FROM sys_attachment WHERE id=100", String.class));
    }

    @Test void foreignOrExpiredAttachmentsAreRejected() {
        file(200, "8", "application/pdf", 100, LocalDateTime.now().plusDays(1));
        file(201, "7", "application/pdf", 100, LocalDateTime.now().minusDays(1));
        for (long id = 200; id <= 201; id++) {
            final long bad = id;
            assertThrows(BusinessException.class, () -> documents.verifyDocument(1L, "tester", "2026-10-14", null, List.of(bad)));
        }
        assertEquals(0, count("sys_attachment_ref"));
        assertEquals(0, jdbc.queryForObject("SELECT document_status FROM purchase_document WHERE document_id=1", Integer.class));
    }
}
