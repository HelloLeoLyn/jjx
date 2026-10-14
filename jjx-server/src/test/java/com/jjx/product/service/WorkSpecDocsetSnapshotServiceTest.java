package com.jjx.product.service;

import com.baomidou.mybatisplus.annotation.TableId;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jjx.common.exception.BusinessException;
import com.jjx.common.utils.Sha256Utils;
import com.jjx.engineering.domain.entity.EngineeringBomItem;
import com.jjx.engineering.domain.entity.EngineeringRoutingItem;
import com.jjx.product.domain.entity.Product;
import com.jjx.product.domain.entity.ProductStandardProcess;
import com.jjx.product.domain.vo.EngineeringBomVO;
import com.jjx.product.mapper.ProductStandardProcessMapper;
import com.jjx.system.domain.entity.SysAttachment;
import com.jjx.system.mapper.SysAttachmentMapper;
import com.jjx.system.service.AttachmentSourcePolicy;
import com.jjx.system.service.ProductCustomerDocumentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 发布完整快照构建器（返修 dev-20261013-010）：客供采集、结构图显式纳入、批准口径、图标字节冻结、
 * 文件字节校验与源一致性。
 */
@ExtendWith(MockitoExtension.class)
class WorkSpecDocsetSnapshotServiceTest {

    @Mock IEngineeringBomService bomService;
    @Mock IEngineeringRoutingService routingService;
    @Mock ProductWorkSpecService workSpecService;
    @Mock SysAttachmentMapper attachmentMapper;
    @Mock ProductStandardProcessMapper processMapper;
    @Mock JdbcTemplate jdbc;
    @Mock ProductCustomerDocumentService customerDocuments;

    WorkSpecDocsetSnapshotService service;
    ObjectMapper json = JsonMapper.builder().addModule(new JavaTimeModule()).build();

    @TempDir Path tempDir;

    @BeforeEach
    void setUp() {
        service = new WorkSpecDocsetSnapshotService(bomService, routingService, workSpecService,
                attachmentMapper, processMapper, jdbc, json, customerDocuments);
        ReflectionTestUtils.setField(service, "uploadBasePath", tempDir.toString());
        ReflectionTestUtils.setField(service, "iconAssetsDir", tempDir.resolve("icons").toString());
        // 默认：正文（未批准、无结构图）、无候选附件、令牌查询空。
        lenient().when(workSpecService.get(anyLong())).thenReturn(spec(null, false));
        lenient().when(attachmentMapper.selectList(any())).thenReturn(List.of());
        lenient().when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());
    }

    private Product product() {
        Product product = new Product();
        product.setProductId(3L);
        product.setProductCode("YL1025PWOO");
        product.setProductName("面膜异形提手");
        product.setCustomerId(9L);
        product.setCustomerName("示例客户");
        product.setProductType("standard");
        product.setCurrentBomId(1L);
        product.setCurrentBomVersion("V1.0");
        return product;
    }

    @Test
    void routingSourceQueryUsesMappedPrimaryKey() throws Exception {
        Product product = product();
        product.setCurrentRouteId(2L);
        ReflectionTestUtils.invokeMethod(service, "sourceToken", product);

        ArgumentCaptor<String> queries = ArgumentCaptor.forClass(String.class);
        verify(jdbc, times(5)).queryForList(queries.capture(), any(Object[].class));
        verify(customerDocuments).lockForSnapshot(product.getProductId());
        String routingSql = queries.getAllValues().stream()
                .filter(sql -> sql.contains("FROM engineering_routing_item "))
                .findFirst().orElseThrow();
        String primaryKey = EngineeringRoutingItem.class.getDeclaredField("itemId")
                .getAnnotation(TableId.class).value();
        assertTrue(routingSql.startsWith("SELECT " + primaryKey + ","), routingSql);
        assertTrue(routingSql.endsWith("WHERE routing_id=? FOR SHARE"), routingSql);
    }

    /**
     * 显式开启时，在只读测试连接上 EXPLAIN 实际来源 SQL；不执行锁读或发布。
     * 环境：JJX_SNAPSHOT_SQL_CHECK=true、JJX_TEST_DB_URL、JJX_TEST_DB_USER=jjx_ro、JJX_TEST_DB_PASSWORD。
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "JJX_SNAPSHOT_SQL_CHECK", matches = "true")
    void sourceQueriesExplainAgainstRealSchema() {
        assertEquals("jjx_ro", System.getenv("JJX_TEST_DB_USER"), "必须使用只读账号");
        DriverManagerDataSource source = new DriverManagerDataSource(
                System.getenv("JJX_TEST_DB_URL"), System.getenv("JJX_TEST_DB_USER"),
                System.getenv("JJX_TEST_DB_PASSWORD"));
        JdbcTemplate database = new JdbcTemplate(source);
        AtomicInteger checked = new AtomicInteger();
        JdbcTemplate validator = new JdbcTemplate() {
            @Override
            public List<Map<String, Object>> queryForList(String sql, Object... args) {
                assertTrue(sql.startsWith("SELECT ") && sql.endsWith("FOR SHARE"), sql);
                database.queryForList("EXPLAIN " + sql, args);
                checked.incrementAndGet();
                return List.of();
            }

            @Override
            public List<Map<String, Object>> queryForList(String sql) {
                return queryForList(sql, new Object[0]);
            }
        };
        ReflectionTestUtils.setField(service, "jdbc", validator);
        ReflectionTestUtils.setField(service, "customerDocuments",
                new ProductCustomerDocumentService(validator, mock(AttachmentSourcePolicy.class)));
        Product product = product();
        product.setCurrentRouteId(2L);
        ReflectionTestUtils.invokeMethod(service, "sourceToken", product);
        assertEquals(7, checked.get(), "BOM/路线各2条，附件2条，工序图标1条");
    }

    /** 规范正文（来自工作态读取：approved 已按 confirmedSource×来源摘要计算）。 */
    private ObjectNode spec(Long structureFileId, boolean approved) {
        ObjectNode node = json.createObjectNode();
        node.put("issueUnit", "工程部");
        node.put("approved", approved);
        if (approved) { node.put("confirmedBy", "张三"); node.put("confirmedAt", "2026-10-12T10:00:00"); }
        node.put("sourceRevision", "src-rev");
        if (structureFileId != null) node.put("structureFileId", structureFileId);
        return node;
    }

    private EngineeringBomVO bom(Long processId) {
        EngineeringBomItem item = new EngineeringBomItem();
        item.setItemId(1L);
        item.setProcessId(processId);
        item.setProcessName("面板");
        item.setMaterialName("PET");
        EngineeringBomVO vo = new EngineeringBomVO();
        vo.setBomId(1L);
        vo.setBomCode("BOM-1");
        vo.setProductId(3L);
        vo.setItems(List.of(item));
        return vo;
    }

    private SysAttachment file(long id, String name, String category) {
        String relative = "files/" + id + "-" + name;
        Path target = tempDir.resolve(relative);
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, "content-of-" + id);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        SysAttachment att = new SysAttachment();
        att.setId(id);
        att.setBizType("product");
        att.setBizId(3L);
        att.setCategory(category);
        att.setFileName(name);
        att.setFilePath(relative);
        att.setFileType("image/jpeg");
        att.setFileSize(100L);
        att.setSha256(Sha256Utils.ofFile(target));
        return att;
    }

    private void stubProcessIcon(long processId, String icon) {
        ProductStandardProcess process = new ProductStandardProcess();
        process.setProcessId(processId);
        process.setIcon(icon);
        when(processMapper.selectById(processId)).thenReturn(process);
    }

    private void writeAsset(String name, String content) {
        try {
            Path dir = tempDir.resolve("icons");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(name + ".svg"), content);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String section(WorkSpecDocsetSnapshotService.DocsetSnapshot snapshot, long id) {
        return snapshot.files().stream().filter(f -> f.attachmentId() == id)
                .map(WorkSpecDocsetSnapshotService.SnapshotFile::section).findFirst().orElseThrow();
    }

    // ---------- 1) 完整性与分类 ----------

    @Test
    void capturesAllSectionsIncludingCustomerLibraryAndExplicitStructureFile() throws Exception {
        writeAsset("面板", "<svg id='icon-面板'></svg>");
        stubProcessIcon(17L, "面板");
        when(workSpecService.get(3L)).thenReturn(spec(40L, true));
        when(bomService.getBomDetail(1L)).thenReturn(bom(17L));

        SysAttachment structure = file(40L, "结构图.jpg", "结构图");
        structure.setIsCurrent(0);
        structure.setReleased(0);
        SysAttachment customerLib = file(36L, "客供稿.jpg", "客供稿");
        SysAttachment sample = file(50L, "样品.jpg", "样品照片");
        SysAttachment customerSource = file(60L, "来料.pdf", null);
        when(attachmentMapper.selectById(40L)).thenReturn(structure);
        when(attachmentMapper.selectById(60L)).thenReturn(customerSource);
        when(attachmentMapper.selectList(any())).thenReturn(List.of(), List.of(sample), List.of(customerLib));
        when(customerDocuments.list(3L)).thenReturn(List.of(Map.of(
                "id", 60L, "display_name", "引用展示名", "customer_doc_type", "CUSTOMER_DRAWING",
                "category", "客户图纸", "sort_order", 7)));

        WorkSpecDocsetSnapshotService.DocsetSnapshot snapshot = service.build(
                product(), "V1.0", "系统管理员", LocalDateTime.of(2026, 10, 13, 12, 0), "首次发布");

        JsonNode node = json.readTree(snapshot.json());
        assertEquals(1, node.path("schemaVersion").asInt());
        assertTrue(node.path("confirmation").path("approved").asBoolean());
        assertEquals("张三", node.path("confirmation").path("confirmedBy").asText());
        List<Long> ids = snapshot.files().stream().map(WorkSpecDocsetSnapshotService.SnapshotFile::attachmentId).toList();
        assertTrue(ids.containsAll(List.of(36L, 40L, 50L, 60L)), ids.toString());
        assertEquals("customer", section(snapshot, 36L));
        assertEquals("customer", section(snapshot, 60L));
        var customerFile = snapshot.files().stream().filter(f -> f.attachmentId() == 60L).findFirst().orElseThrow();
        assertEquals("引用展示名", customerFile.displayName());
        assertEquals("CUSTOMER_DRAWING", customerFile.customerDocType());
        assertEquals("客户图纸", customerFile.category());
        assertEquals(7, customerFile.sortOrder());
        assertEquals("atlas", section(snapshot, 40L));
        assertEquals("sample", section(snapshot, 50L));
        JsonNode icon = node.path("icons").path("面板");
        assertEquals("bundled", icon.path("kind").asText());
        assertEquals(64, icon.path("sha256").asText().length());
        assertTrue(Files.isRegularFile(tempDir.resolve(icon.path("asset").asText())));
        assertEquals(icon.path("sha256").asText(), node.path("bom").path("items").get(0).path("iconSha256").asText());
    }

    // ---------- 2) 批准口径 ----------

    @Test
    void unapprovedSpecStaysUnapproved() throws Exception {
        WorkSpecDocsetSnapshotService.DocsetSnapshot snapshot = service.build(
                productBomless(), "V1.0", "管理员", LocalDateTime.now(), null);
        JsonNode node = json.readTree(snapshot.json());
        assertFalse(node.path("confirmation").path("approved").asBoolean());
        assertTrue(node.path("confirmation").path("confirmedBy").isNull());
    }

    private Product productBomless() {
        Product product = product();
        product.setCurrentBomId(null);
        return product;
    }

    @Test
    void changedReferenceMetadataDuringPublicationRejectsSnapshot() {
        when(customerDocuments.lockForSnapshot(3L)).thenReturn(
                List.of(Map.of("ref_id", 9L, "display_name", "旧展示名")),
                List.of(Map.of("ref_id", 9L, "display_name", "新展示名")));
        BusinessException error = assertThrows(BusinessException.class, () -> service.build(
                productBomless(), "V1.0", "管理员", LocalDateTime.now(), null));
        assertTrue(error.getMessage().contains("发布来源在读取期间发生变化"));
    }

    // ---------- 3) 图标冻结失败反例 ----------

    @Test
    void missingBundledIconAssetFails() {
        writeAsset("面板", "<svg/>");
        stubProcessIcon(17L, "不存在的图标");
        when(bomService.getBomDetail(1L)).thenReturn(bom(17L));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.build(
                product(), "V1.0", "管理员", LocalDateTime.now(), null));
        assertTrue(ex.getMessage().contains("打包图标资源缺失"));
    }

    @Test
    void externalIconUrlRejected() {
        stubProcessIcon(17L, "https://example.com/icon.png");
        when(bomService.getBomDetail(1L)).thenReturn(bom(17L));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.build(
                product(), "V1.0", "管理员", LocalDateTime.now(), null));
        assertTrue(ex.getMessage().contains("外部URL"));
    }

    @Test
    void uploadedIconArchivedAndIncludedAsProtectedFile() {
        stubProcessIcon(17L, "/system/attachment/download/55");
        SysAttachment iconAtt = file(55L, "custom.png", null);
        iconAtt.setBizType("engineering_jjx_icon");
        when(attachmentMapper.selectById(55L)).thenReturn(iconAtt);
        when(bomService.getBomDetail(1L)).thenReturn(bom(17L));

        WorkSpecDocsetSnapshotService.DocsetSnapshot snapshot = service.build(
                product(), "V1.0", "管理员", LocalDateTime.now(), null);
        assertTrue(snapshot.files().stream().anyMatch(f -> f.attachmentId() == 55L && "icon".equals(f.section())));
        assertTrue(snapshot.icons().stream().anyMatch(r -> "uploaded".equals(r.kind()) && r.attachmentId() == 55L));
    }

    // ---------- 4) 文件校验失败 ----------

    @Test
    void missingFileFails() {
        when(workSpecService.get(3L)).thenReturn(spec(40L, true));
        SysAttachment broken = file(40L, "结构图.jpg", "结构图");
        broken.setFilePath("files/does-not-exist.jpg");
        when(attachmentMapper.selectById(40L)).thenReturn(broken);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.build(
                productBomless(), "V1.0", "管理员", LocalDateTime.now(), null));
        assertTrue(ex.getMessage().contains("缺失"));
    }

    @Test
    void hashMismatchFails() {
        when(workSpecService.get(3L)).thenReturn(spec(40L, true));
        SysAttachment tampered = file(40L, "结构图.jpg", "结构图");
        tampered.setSha256("0".repeat(64));
        when(attachmentMapper.selectById(40L)).thenReturn(tampered);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.build(
                productBomless(), "V1.0", "管理员", LocalDateTime.now(), null));
        assertTrue(ex.getMessage().contains("哈希不符"));
    }

    @Test
    void missingBomFails() {
        when(bomService.getBomDetail(1L)).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.build(
                product(), "V1.0", "管理员", LocalDateTime.now(), null));
    }

    // ---------- 5) 并发一致性 ----------

    @Test
    void concurrentSourceChangeFails() {
        when(bomService.getBomDetail(1L)).thenReturn(bom(null));
        AtomicInteger calls = new AtomicInteger();
        lenient().when(jdbc.queryForList(anyString(), any(Object[].class))).thenAnswer(inv ->
                calls.getAndIncrement() < 5 ? List.of() : List.of(Map.of("changed", 1)));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.build(
                product(), "V1.0", "管理员", LocalDateTime.now(), null));
        assertTrue(ex.getMessage().contains("发生变化"));
    }
}
