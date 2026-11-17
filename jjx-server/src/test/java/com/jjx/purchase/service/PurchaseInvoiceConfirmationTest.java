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
    MockedStatic<com.jjx.system.utils.SecurityUtils> identity;
    PurchaseDocumentMapper documentMapper;
    PurchaseOrderMapper orderMapper;

    @BeforeEach void setup() {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE purchase_document(document_id BIGINT AUTO_INCREMENT PRIMARY KEY,document_no VARCHAR(50),document_type VARCHAR(20),document_status INT,file_url VARCHAR(500),remark VARCHAR(500),verification_date DATE,update_time TIMESTAMP,verified_by VARCHAR(64),verifier_name VARCHAR(64),verified_time TIMESTAMP,verification_remark VARCHAR(500),invoice_no VARCHAR(50) UNIQUE,order_id BIGINT,supplier_id BIGINT,document_amount DECIMAL(12,2) DEFAULT 0,untaxed_amount DECIMAL(12,2),tax_amount DECIMAL(12,2))");
        jdbc.execute("CREATE TABLE sys_attachment(id BIGINT PRIMARY KEY,upload_state VARCHAR(20),owner VARCHAR(20),mime VARCHAR(80),size BIGINT,expires TIMESTAMP,deleted TINYINT DEFAULT 0)");
        jdbc.execute("CREATE TABLE sys_attachment_ref(id BIGINT AUTO_INCREMENT PRIMARY KEY,attachment_id BIGINT,source_id BIGINT,source_type VARCHAR(50),deleted TINYINT DEFAULT 0,ref_state VARCHAR(20) DEFAULT 'active',document_type VARCHAR(100),create_by VARCHAR(64))");
        jdbc.update("INSERT INTO purchase_document(document_id,document_no,document_type,document_status) VALUES(1,'INV-1','invoice',0)");
        jdbc.update("UPDATE purchase_document SET invoice_no='REAL-1' WHERE document_id=1");
        file(100, "7", "application/pdf", 100, LocalDateTime.now().plusDays(1));
        file(101, "7", "image/png", 100, LocalDateTime.now().plusDays(1));
        auth = mockStatic(StpUtil.class);
        auth.when(StpUtil::getLoginIdAsString).thenReturn("7");
        identity = mockStatic(com.jjx.system.utils.SecurityUtils.class);
        identity.when(com.jjx.system.utils.SecurityUtils::getDisplayName).thenReturn("实际核验人");

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

        documentMapper = mock(PurchaseDocumentMapper.class);
        when(documentMapper.lockById(anyLong())).thenAnswer(c -> {
            var rows = jdbc.queryForList("SELECT * FROM purchase_document WHERE document_id=?", c.getArgument(0, Long.class));
            if (rows.isEmpty()) return null;
            var r = rows.get(0); var d = new PurchaseDocument();
            d.setDocumentId(((Number) r.get("document_id")).longValue());
            d.setDocumentNo((String) r.get("document_no"));
            d.setDocumentType((String) r.get("document_type"));
            d.setDocumentStatus(((Number) r.get("document_status")).intValue());
            d.setFileUrl((String) r.get("file_url"));
            d.setInvoiceNo((String) r.get("invoice_no")); d.setSellerName("销售方"); d.setSellerTaxNo("SELLER-TAX");
            d.setBuyerName("购买方"); d.setBuyerTaxNo("BUYER-TAX"); d.setCurrency("CNY");
            d.setDocumentDate(java.time.LocalDate.of(2026, 11, 17));
            d.setUntaxedAmount(new java.math.BigDecimal("100.00"));
            d.setTaxAmount(new java.math.BigDecimal("13.00"));
            d.setDocumentAmount(new java.math.BigDecimal("113.00"));
            d.setRemark((String) r.get("remark"));
            return d;
        });
        when(documentMapper.updateById(any(PurchaseDocument.class))).thenAnswer(c -> {
            var d = (PurchaseDocument) c.getArgument(0);
            return jdbc.update("UPDATE purchase_document SET document_status=?,verified_by=?,verifier_name=?,verified_time=?,verification_remark=?,remark=? WHERE document_id=?", d.getDocumentStatus(), d.getVerifiedBy(), d.getVerifierName(), d.getVerifiedTime(), d.getVerificationRemark(), d.getRemark(), d.getDocumentId());
        });
        when(documentMapper.insert(any(PurchaseDocument.class))).thenAnswer(c -> {
            var d = c.getArgument(0, PurchaseDocument.class);
            jdbc.update("INSERT INTO purchase_document(document_no,document_type,document_status,invoice_no,order_id,supplier_id,document_amount,untaxed_amount,tax_amount) VALUES(?,?,?,?,?,?,?,?,?)",
                d.getDocumentNo(), d.getDocumentType(), d.getDocumentStatus(), d.getInvoiceNo(), d.getOrderId(), d.getSupplierId(), d.getDocumentAmount(), d.getUntaxedAmount(), d.getTaxAmount());
            d.setDocumentId(jdbc.queryForObject("SELECT document_id FROM purchase_document WHERE invoice_no=?", Long.class, d.getInvoiceNo()));
            return 1;
        });
        when(documentMapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenReturn(List.of());
        when(documentMapper.selectCount(any())).thenAnswer(c -> jdbc.queryForObject("SELECT COUNT(*) FROM purchase_document WHERE invoice_no='REAL-NEW'", Long.class));
        when(documentMapper.sumInvoiceAmount(anyLong(), nullable(Long.class))).thenReturn(java.math.BigDecimal.ZERO);
        orderMapper = mock(PurchaseOrderMapper.class);
        var order = new com.jjx.purchase.domain.entity.PurchaseOrder();
        order.setOrderId(10L); order.setSupplierId(20L); order.setCurrency("CNY");
        order.setReceiptStatus(com.jjx.purchase.domain.enums.ReceiptStatusEnum.PARTIALLY_RECEIVED.getValue());
        order.setOrderTotalAmount(new java.math.BigDecimal("1000.00"));
        when(orderMapper.lockById(10L)).thenReturn(order);

        var service = mock(PurchaseDocumentServiceImpl.class, CALLS_REAL_METHODS);
        ReflectionTestUtils.setField(service, "documentMapper", documentMapper);
        ReflectionTestUtils.setField(service, "invoiceAttachments", attachments);
        ReflectionTestUtils.setField(service, "purchaseOrderMapper", orderMapper);
        ReflectionTestUtils.setField(service, "eventPublisher", mock(EventPublisher.class));
        var proxy = new ProxyFactory(service);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(ds), new AnnotationTransactionAttributeSource()));
        documents = (IPurchaseDocumentService) proxy.getProxy();
    }

    @AfterEach void close() { auth.close(); identity.close(); }

    void file(long id, String owner, String mime, long size, LocalDateTime expiry) {
        jdbc.update("INSERT INTO sys_attachment(id,upload_state,owner,mime,size,expires) VALUES(?,?,?,?,?,?)", id, AttachmentUploadState.TEMP.name(), owner, mime, size, expiry);
        jdbc.update("INSERT INTO sys_attachment_ref(attachment_id,ref_state,document_type,create_by) VALUES(?,'draft','VAT_SPECIAL_INVOICE',?)", id, owner);
    }
    int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + (table.equals("sys_attachment_ref") ? " WHERE ref_state='active'" : ""), Integer.class); }

    @Test void verifyBindsTempAttachmentsInSameTransaction() {
        assertEquals(1, documents.verifyDocument(1L, "tester", "2026-10-14", "ok", List.of(100L)));
        assertEquals(1, jdbc.queryForObject("SELECT document_status FROM purchase_document WHERE document_id=1", Integer.class));
        assertEquals(1, count("sys_attachment_ref"));
        assertEquals("purchase_invoice", jdbc.queryForObject("SELECT source_type FROM sys_attachment_ref WHERE ref_state='active'", String.class));
        assertEquals(1L, jdbc.queryForObject("SELECT source_id FROM sys_attachment_ref WHERE ref_state='active'", Long.class));
        assertEquals(AttachmentUploadState.BOUND.name(), jdbc.queryForObject("SELECT upload_state FROM sys_attachment WHERE id=100", String.class));
    }

    @Test void verifyBindsCarriedAttachmentFromFileUrl() {
        jdbc.update("UPDATE purchase_document SET file_url='/system/attachment/download/101' WHERE document_id=1");
        assertEquals(1, documents.verifyDocument(1L, "tester", "2026-10-14", null));
        assertEquals(1, count("sys_attachment_ref"));
        assertEquals(101L, jdbc.queryForObject("SELECT attachment_id FROM sys_attachment_ref WHERE ref_state='active'", Long.class));
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

    @Test void actualOperatorIsRecordedAndRegistrationRemarkSurvives() {
        jdbc.update("UPDATE purchase_document SET remark='原登记备注' WHERE document_id=1");
        documents.verifyDocument(1L, "伪造姓名", "2026-11-17", "票面与订单已核对", List.of(100L));
        var row = jdbc.queryForMap("SELECT * FROM purchase_document WHERE document_id=1");
        assertEquals("7", row.get("verified_by"));
        assertEquals("实际核验人", row.get("verifier_name"));
        assertNotNull(row.get("verified_time"));
        assertEquals("原登记备注", row.get("remark"));
        assertEquals("票面与订单已核对", row.get("verification_remark"));
        assertThrows(BusinessException.class, () -> documents.verifyDocument(1L, "其他人", "2026-11-17", null));
    }

    @Test void registeredAttachmentsAreNotReboundAtVerification() {
        jdbc.update("INSERT INTO sys_attachment_ref(attachment_id,source_id,source_type) VALUES(100,1,'purchase_invoice')");
        jdbc.update("UPDATE purchase_document SET file_url='/system/attachment/download/100' WHERE document_id=1");
        jdbc.update("UPDATE sys_attachment SET upload_state='BOUND' WHERE id=100");
        assertEquals(1, documents.verifyDocument(1L, "ignored", "2026-11-17", null));
        verify(refs, never()).create(any());
    }

    @Test void missingOriginalCannotBeVerified() {
        assertThrows(BusinessException.class, () -> documents.verifyDocument(1L, "ignored", "2026-11-17", null));
        assertEquals(0, jdbc.queryForObject("SELECT document_status FROM purchase_document WHERE document_id=1", Integer.class));
    }

    com.jjx.purchase.domain.dto.PurchaseInvoiceRegistrationDTO registration() {
        var dto = new com.jjx.purchase.domain.dto.PurchaseInvoiceRegistrationDTO();
        dto.setOrderId(10L); dto.setInvoiceNo("REAL-NEW"); dto.setDocumentDate(java.time.LocalDate.of(2026, 11, 1));
        dto.setSellerName("销售方"); dto.setSellerTaxNo("SELLER-TAX"); dto.setBuyerName("购买方"); dto.setBuyerTaxNo("BUYER-TAX");
        dto.setUntaxedAmount(new java.math.BigDecimal("100.00")); dto.setTaxAmount(new java.math.BigDecimal("13.00"));
        dto.setDocumentAmount(new java.math.BigDecimal("113.00")); dto.setCurrency("CNY");
        dto.setAttachments(java.util.stream.Stream.of(100L, 101L).map(id -> {
            var file = new com.jjx.sales.domain.dto.AttachmentBindDTO(); file.setAttachmentId(id); file.setDocumentType("VAT_SPECIAL_INVOICE"); return file;
        }).toList());
        return dto;
    }

    @Test void registrationCreatesOneInvoiceWithMultipleBoundFiles() {
        Long id = documents.registerInvoice(null, registration());
        assertEquals(2, count("purchase_document"));
        assertEquals(2, count("sys_attachment_ref"));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM sys_attachment_ref WHERE source_id=? AND ref_state='active'", Integer.class, id));
        assertEquals(new java.math.BigDecimal("113.00"), jdbc.queryForObject("SELECT document_amount FROM purchase_document WHERE document_id=?", java.math.BigDecimal.class, id));
        var calls = org.mockito.ArgumentCaptor.forClass(AttachmentRefDTO.class);
        verify(refs, times(2)).create(calls.capture());
        assertTrue(calls.getAllValues().stream().allMatch(ref -> "VAT_SPECIAL_INVOICE".equals(ref.getDocumentType())));
        assertThrows(BusinessException.class, () -> documents.registerInvoice(null, registration()));
    }

    @Test void registrationBindingFailureRollsBackInvoiceAndAllFiles() {
        doAnswer(call -> { throw new BusinessException("第二份文件绑定失败"); }).when(refs).create(argThat(ref -> ref.getAttachmentId().equals(101L)));
        assertThrows(BusinessException.class, () -> documents.registerInvoice(null, registration()));
        assertEquals(1, count("purchase_document")); assertEquals(0, count("sys_attachment_ref"));
        assertEquals(AttachmentUploadState.TEMP.name(), jdbc.queryForObject("SELECT upload_state FROM sys_attachment WHERE id=100", String.class));
    }

    @Test void invalidTotalsAndOverInvoicingAreRejected() {
        var dto = registration(); dto.setDocumentAmount(new java.math.BigDecimal("114.00"));
        assertThrows(BusinessException.class, () -> documents.registerInvoice(null, dto));
        dto.setDocumentAmount(new java.math.BigDecimal("113.00"));
        when(documentMapper.sumInvoiceAmount(10L, null)).thenReturn(new java.math.BigDecimal("900.00"));
        assertThrows(BusinessException.class, () -> documents.registerInvoice(null, dto));
        assertEquals(1, count("purchase_document"));
    }

    @Test void incompleteHistoricalInvoiceCannotBeVerified() {
        jdbc.update("UPDATE purchase_document SET invoice_no=NULL WHERE document_id=1");
        assertThrows(BusinessException.class, () -> documents.verifyDocument(1L, "ignored", "2026-11-17", null, List.of(100L)));
        assertEquals(0, jdbc.queryForObject("SELECT document_status FROM purchase_document WHERE document_id=1", Integer.class));
    }
}
