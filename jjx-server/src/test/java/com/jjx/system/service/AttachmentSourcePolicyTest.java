package com.jjx.system.service;

import cn.dev33.satoken.stp.StpUtil;
import com.jjx.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AttachmentSourcePolicyTest {
    @Test
    void workSpecUsesProductIdentityPermissionsAndOwnership() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(contains("FROM product"), eq(String.class), eq(42L)))
            .thenReturn(List.of("P42"));
        when(jdbc.queryForObject(contains("COUNT(*) FROM product"), eq(Long.class), eq(42L), eq(42L)))
            .thenReturn(1L);
        AttachmentSourcePolicy policy = new AttachmentSourcePolicy(jdbc);
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::isLogin).thenReturn(true);
            assertEquals("P42", policy.check("work_spec", 42L, true));
            auth.verify(() -> StpUtil.checkPermission("product:edit"));
            assertEquals("P42", policy.check("work_spec", 42L, false));
            auth.verify(() -> StpUtil.checkPermission("product:view"));
            assertFalse(policy.canRead("work_spec", 42L));
            auth.when(() -> StpUtil.hasPermission("product:view")).thenReturn(true);
            assertTrue(policy.canRead("work_spec", 42L));
            assertThrows(BusinessException.class, () -> policy.check("work_spec", 99L, true));
            policy.checkProduct("work_spec", 42L, 42L);
            assertThrows(BusinessException.class, () -> policy.checkProduct("work_spec", 41L, 42L));
        }
    }

    @Test
    void singleProductOfReturnsOnlyWhenExactlyOneProduct() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(contains("sales_quotation_item"), org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<AttachmentSourcePolicy.ProductRef>>any(), eq(1L)))
                .thenReturn(List.of(new AttachmentSourcePolicy.ProductRef(2L, "YL002MEOO")));
        when(jdbc.query(contains("sales_order_product"), org.mockito.ArgumentMatchers.<org.springframework.jdbc.core.RowMapper<AttachmentSourcePolicy.ProductRef>>any(), eq(9L)))
                .thenReturn(List.of(new AttachmentSourcePolicy.ProductRef(2L, "A"), new AttachmentSourcePolicy.ProductRef(3L, "B")));
        AttachmentSourcePolicy policy = new AttachmentSourcePolicy(jdbc);

        assertEquals(2L, policy.singleProductOf("quotation", 1L).id());
        assertEquals("YL002MEOO", policy.singleProductOf("quotation", 1L).code());
        assertNull(policy.singleProductOf("sales_order", 9L)); // 多产品不猜
        assertNull(policy.singleProductOf("purchase_invoice", 1L)); // 无产品来源不登记
        assertNull(policy.singleProductOf("quotation", null)); // 无来源ID
    }

    @Test
    void taskAttachmentsUseBoardLoginBoundaryAndRequireExistingTask() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(contains("FROM sys_task"), eq(String.class), eq(42L)))
                .thenReturn(List.of("dev-20261014-001"));
        AttachmentSourcePolicy policy = new AttachmentSourcePolicy(jdbc);

        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::isLogin).thenReturn(true);
            assertEquals("dev-20261014-001", policy.check("task", 42L, false));
            assertEquals("dev-20261014-001", policy.check("task", 42L, true));
            assertTrue(policy.canRead("task", 42L));
            assertThrows(BusinessException.class, () -> policy.check("task", 43L, false));
            assertThrows(BusinessException.class, () -> policy.check("unknown", 42L, false));
        }
    }
    @Test
    void receiptUsesDocumentPermissionsAndPurchaseInboundIdentity() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(contains("source_type='PURCHASE'"), eq(String.class), eq(88L)))
                .thenReturn(List.of("IN-88"));
        AttachmentSourcePolicy policy = new AttachmentSourcePolicy(jdbc);
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::isLogin).thenReturn(true);
            assertEquals("IN-88", policy.check("purchase_receipt", 88L, true));
            auth.verify(() -> StpUtil.checkPermission("purchase:receipt:doc:add"));
            assertEquals("IN-88", policy.check("purchase_receipt", 88L, false));
            auth.verify(() -> StpUtil.checkPermission("purchase:receipt:doc:view"));
            assertFalse(policy.canRead("purchase_receipt", 88L));
            auth.when(() -> StpUtil.hasPermission("purchase:receipt:doc:view")).thenReturn(true);
            assertTrue(policy.canRead("purchase_receipt", 88L));
            assertFalse(policy.canRead("purchase_receipt", 89L));
            assertThrows(BusinessException.class, () -> policy.check("purchase_receipt", 89L, true));
        }
    }
    @Test
    void invoiceUsesDocumentPermissionsAndPurchaseInvoiceIdentity() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(contains("document_type='invoice'"), eq(String.class), eq(88L)))
                .thenReturn(List.of("INV-88"));
        AttachmentSourcePolicy policy = new AttachmentSourcePolicy(jdbc);
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::isLogin).thenReturn(true);
            assertEquals("INV-88", policy.check("purchase_invoice", 88L, true));
            auth.verify(() -> StpUtil.checkPermission("purchase:invoice:doc:add"));
            assertEquals("INV-88", policy.check("purchase_invoice", 88L, false));
            auth.verify(() -> StpUtil.checkPermission("purchase:invoice:doc:view"));
            assertFalse(policy.canRead("purchase_invoice", 88L));
            auth.when(() -> StpUtil.hasPermission("purchase:invoice:doc:view")).thenReturn(true);
            assertTrue(policy.canRead("purchase_invoice", 88L));
            assertFalse(policy.canRead("purchase_invoice", 89L));
            assertThrows(BusinessException.class, () -> policy.check("purchase_invoice", 89L, true));
        }
    }
    @Test
    void salesDeliveryUsesDocumentPermissionsAndDeliveryIdentity() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(contains("FROM sales_delivery"), eq(String.class), eq(88L)))
                .thenReturn(List.of("DL-88"));
        AttachmentSourcePolicy policy = new AttachmentSourcePolicy(jdbc);
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::isLogin).thenReturn(true);
            // 写权限为 :add（凭证式，与收货票据/采购发票同款）
            assertEquals("DL-88", policy.check("sales_delivery", 88L, true));
            auth.verify(() -> StpUtil.checkPermission("sales:delivery:doc:add"));
            assertEquals("DL-88", policy.check("sales_delivery", 88L, false));
            auth.verify(() -> StpUtil.checkPermission("sales:delivery:doc:view"));
            assertFalse(policy.canRead("sales_delivery", 88L));
            auth.when(() -> StpUtil.hasPermission("sales:delivery:doc:view")).thenReturn(true);
            assertTrue(policy.canRead("sales_delivery", 88L));
            assertFalse(policy.canRead("sales_delivery", 89L));
            assertThrows(BusinessException.class, () -> policy.check("sales_delivery", 89L, true));
            // 无 trace 列：traceIdOf 返回 null（不引入跨表分层）
            assertNull(policy.traceIdOf("sales_delivery", 88L));
        }
    }

    @Test
    void qualityArchiveUsesDocumentPermissionsAndPrintLogIdentity() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(contains("FROM quality_template_print_log"), eq(String.class), eq(88L)))
                .thenReturn(List.of("JJX-QR-001"));
        AttachmentSourcePolicy policy = new AttachmentSourcePolicy(jdbc);
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::isLogin).thenReturn(true);
            // 写权限为 :add（凭证式，与收货票据/采购发票/送货回签件同款）
            assertEquals("JJX-QR-001", policy.check("quality_archive", 88L, true));
            auth.verify(() -> StpUtil.checkPermission("production:quality-archive:doc:add"));
            assertEquals("JJX-QR-001", policy.check("quality_archive", 88L, false));
            auth.verify(() -> StpUtil.checkPermission("production:quality-archive:doc:view"));
            assertFalse(policy.canRead("quality_archive", 88L));
            auth.when(() -> StpUtil.hasPermission("production:quality-archive:doc:view")).thenReturn(true);
            assertTrue(policy.canRead("quality_archive", 88L));
            assertFalse(policy.canRead("quality_archive", 89L));
            assertThrows(BusinessException.class, () -> policy.check("quality_archive", 89L, true));
            // 无 trace 列：traceIdOf 返回 null（不引入跨表分层）
            assertNull(policy.traceIdOf("quality_archive", 88L));
        }
    }

    @Test
    void qualityTemplateUsesDocumentPermissionsAndRecordNoIdentity() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(contains("FROM quality_template_registry WHERE id=?"), eq(String.class), eq(88L)))
                .thenReturn(List.of("JJX-QR-001"));
        AttachmentSourcePolicy policy = new AttachmentSourcePolicy(jdbc);
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::isLogin).thenReturn(true);
            // 写权限为 :add（凭证式，与收货票据/采购发票/送货回签件/质量档案同款）
            assertEquals("JJX-QR-001", policy.check("quality_template", 88L, true));
            auth.verify(() -> StpUtil.checkPermission("production:quality-template:doc:add"));
            assertEquals("JJX-QR-001", policy.check("quality_template", 88L, false));
            auth.verify(() -> StpUtil.checkPermission("production:quality-template:doc:view"));
            assertFalse(policy.canRead("quality_template", 88L));
            auth.when(() -> StpUtil.hasPermission("production:quality-template:doc:view")).thenReturn(true);
            assertTrue(policy.canRead("quality_template", 88L));
            assertFalse(policy.canRead("quality_template", 89L));
            assertThrows(BusinessException.class, () -> policy.check("quality_template", 89L, true));
            // 无 trace 列：traceIdOf 返回 null（不引入跨表分层）
            assertNull(policy.traceIdOf("quality_template", 88L));
        }
    }

    @Test
    void sourceAllowlistIsSingleSourceOfTruthForDictionary() {
        // 收敛后：白名单唯一真源含全部来源（含凭证类），字典 ext_data.sources 必须取自本集合。
        assertTrue(AttachmentSourcePolicy.knownSourceTypes().containsAll(List.of(
            "product", "inquiry", "quotation", "sales_order", "sample", "production_order",
            "purchase_order", "purchase_payment", "task", "material",
            "purchase_receipt", "purchase_invoice", "sales_delivery", "quality_archive",
            "quality_template")));
    }

    @Test
    void receiptResolvesIdByNumberAndTraceThroughUnifiedConfig() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(contains("inventory_inbound_order WHERE inbound_no=?"), eq(Long.class), eq("IN-88")))
                .thenReturn(List.of(88L));
        when(jdbc.queryForList(contains("inventory_inbound_order WHERE inbound_id=?"), eq(String.class), eq(88L)))
                .thenReturn(List.of("TR-88"));
        AttachmentSourcePolicy policy = new AttachmentSourcePolicy(jdbc);
        try (MockedStatic<StpUtil> auth = mockStatic(StpUtil.class)) {
            auth.when(StpUtil::isLogin).thenReturn(true);
            assertEquals(88L, policy.resolveReadId("purchase_receipt", "IN-88"));
            auth.verify(() -> StpUtil.checkPermission("purchase:receipt:doc:view"));
            assertEquals("TR-88", policy.traceIdOf("purchase_receipt", 88L));
        }
    }
}
