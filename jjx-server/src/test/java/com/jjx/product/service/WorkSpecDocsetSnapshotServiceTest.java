package com.jjx.product.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jjx.common.exception.BusinessException;
import com.jjx.common.utils.Sha256Utils;
import com.jjx.engineering.domain.entity.EngineeringBomItem;
import com.jjx.product.domain.entity.Product;
import com.jjx.product.domain.entity.ProductStandardProcess;
import com.jjx.product.domain.vo.EngineeringBomVO;
import com.jjx.product.domain.vo.EngineeringRoutingItemVO;
import com.jjx.product.domain.vo.EngineeringRoutingVO;
import com.jjx.product.mapper.ProductStandardProcessMapper;
import com.jjx.system.domain.entity.SysAttachment;
import com.jjx.system.mapper.SysAttachmentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 发布完整快照构建器（dev-20261013-010）：完整性、图标冻结、文件字节校验与源一致性。
 */
@ExtendWith(MockitoExtension.class)
class WorkSpecDocsetSnapshotServiceTest {

    @Mock IEngineeringBomService bomService;
    @Mock IEngineeringRoutingService routingService;
    @Mock SysAttachmentMapper attachmentMapper;
    @Mock ProductStandardProcessMapper processMapper;

    WorkSpecDocsetSnapshotService service;
    ObjectMapper json = JsonMapper.builder().addModule(new JavaTimeModule()).build();

    @TempDir Path tempDir;

    @BeforeEach
    void setUp() {
        service = new WorkSpecDocsetSnapshotService(bomService, routingService, attachmentMapper, processMapper, json);
        ReflectionTestUtils.setField(service, "uploadBasePath", tempDir.toString());
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
        product.setCurrentRouteId(1L);
        product.setCurrentRoutingVersion("V1.0");
        return product;
    }

    private EngineeringBomVO bom() {
        EngineeringBomItem item = new EngineeringBomItem();
        item.setItemId(1L);
        item.setProcessId(17L);
        item.setProcessName("印刷");
        item.setMaterialName("PET");
        EngineeringBomVO vo = new EngineeringBomVO();
        vo.setBomId(1L);
        vo.setBomCode("BOM-1");
        vo.setProductId(3L);
        vo.setItems(List.of(item));
        return vo;
    }

    private EngineeringRoutingVO routing() {
        EngineeringRoutingItemVO item = EngineeringRoutingItemVO.builder()
                .itemId(1L).processId(20L).processName("印黑").icon("routing-icon").build();
        EngineeringRoutingVO vo = new EngineeringRoutingVO();
        vo.setRoutingId(1L);
        vo.setRoutingCode("RT-1");
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

    @Test
    void capturesProductSpecConfirmationBomRoutingAndAllFileSections() throws Exception {
        // 材料图标仅存于字典表；发布时按 processId 冻结进快照。
        ProductStandardProcess cut = new ProductStandardProcess();
        cut.setProcessId(17L);
        cut.setIcon("cut");
        when(processMapper.selectById(17L)).thenReturn(cut);
        ProductStandardProcess print = new ProductStandardProcess();
        print.setProcessId(20L);
        print.setIcon("print");
        when(processMapper.selectById(20L)).thenReturn(print);

        when(bomService.getBomDetail(1L)).thenReturn(bom());
        when(routingService.getRoutingItems(1L)).thenReturn(routing());

        SysAttachment drawing = file(40L, "结构图.jpg", "结构图");
        SysAttachment sample = file(50L, "样品.jpg", "样品照片");
        SysAttachment customer = file(60L, "客供.pdf", "客供稿");
        customer.setCustomerProductCode("YL1025PWOO");
        when(attachmentMapper.selectList(any())).thenReturn(List.of(drawing), List.of(sample), List.of(customer));

        String spec = "{\"issueUnit\":\"工程部\",\"approved\":true,\"confirmedBy\":\"张三\",\"issueDate\":\"2026-10-12\"}";
        WorkSpecDocsetSnapshotService.DocsetSnapshot snapshot =
                service.build(product(), spec, "V3.0", "系统管理员", LocalDateTime.of(2026, 10, 13, 12, 0), "新增印刷工艺");

        JsonNode node = json.readTree(snapshot.json());
        assertEquals(1, node.path("schemaVersion").asInt());
        assertEquals("YL1025PWOO", node.path("productCode").asText());
        assertEquals("工程部", node.path("confirmation").path("issueUnit").asText());
        assertTrue(node.path("confirmation").path("approved").asBoolean());
        assertEquals("V3.0", node.path("publication").path("versionNo").asText());
        // 材料图标冻结进 BOM 条目
        assertEquals("cut", node.path("bom").path("items").get(0).path("icon").asText());
        // 路线项图标保留
        assertEquals("print", node.path("routing").path("items").get(0).path("icon").asText());
        // 三类文件全部入集且分类正确
        assertEquals(3, snapshot.files().size());
        assertEquals(3, node.path("counts").path("files").asInt());
        assertEquals(1, node.path("counts").path("bomItems").asInt());
        assertEquals(1, node.path("counts").path("routingItems").asInt());
        List<String> sections = snapshot.files().stream()
                .map(WorkSpecDocsetSnapshotService.SnapshotFile::section).toList();
        assertTrue(sections.containsAll(List.of("atlas", "sample", "customer")));
        // 清单哈希为 64 位 hex，且与写入值一致
        assertEquals(64, snapshot.manifestHash().length());
        assertEquals(snapshot.manifestHash(), node.path("manifestHash").asText());
    }

    @Test
    void missingFileFailsWithoutProducingSnapshot() {
        SysAttachment drawing = file(40L, "结构图.jpg", "结构图");
        drawing.setFilePath("files/does-not-exist.jpg");
        when(attachmentMapper.selectList(any())).thenReturn(List.of(drawing));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.build(productWithoutBomOrRouting(), "{}", "V1.0", "管理员", LocalDateTime.now(), null));
        assertTrue(ex.getMessage().contains("缺失"));
    }

    @Test
    void hashMismatchFails() {
        SysAttachment drawing = file(40L, "结构图.jpg", "结构图");
        drawing.setSha256("0".repeat(64));
        when(attachmentMapper.selectList(any())).thenReturn(List.of(drawing));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.build(productWithoutBomOrRouting(), "{}", "V1.0", "管理员", LocalDateTime.now(), null));
        assertTrue(ex.getMessage().contains("哈希不符"));
    }

    @Test
    void invalidSpecJsonFails() {
        assertThrows(BusinessException.class,
                () -> service.build(product(), "{not json", "V1.0", "管理员", LocalDateTime.now(), null));
    }

    @Test
    void missingBomFails() {
        when(bomService.getBomDetail(1L)).thenReturn(null);
        assertThrows(BusinessException.class,
                () -> service.build(product(), "{}", "V1.0", "管理员", LocalDateTime.now(), null));
    }

    @Test
    void concurrentBomChangeFailsPublish() {
        when(bomService.getBomDetail(1L)).thenReturn(bom(), routingBomMutated());
        when(attachmentMapper.selectList(any())).thenReturn(List.of());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.build(productWithoutRouting(), "{}", "V1.0", "管理员", LocalDateTime.now(), null));
        assertTrue(ex.getMessage().contains("发生变化"));
    }

    private EngineeringBomVO routingBomMutated() {
        EngineeringBomVO vo = bom();
        vo.setBomCode("BOM-CHANGED");
        return vo;
    }

    private Product productWithoutRouting() {
        Product product = product();
        product.setCurrentRouteId(null);
        return product;
    }

    private Product productWithoutBomOrRouting() {
        Product product = product();
        product.setCurrentBomId(null);
        product.setCurrentRouteId(null);
        return product;
    }

    @Test
    void rebuildingAfterSourceChangeYieldsNewSnapshotButOldStringUnchanged() throws Exception {
        when(bomService.getBomDetail(1L)).thenReturn(bom());
        when(routingService.getRoutingItems(1L)).thenReturn(routing());
        when(attachmentMapper.selectList(any())).thenReturn(List.of());

        WorkSpecDocsetSnapshotService.DocsetSnapshot first =
                service.build(product(), "{\"engineeringRequirements\":\"旧要求\"}", "V1.0", "管理员", LocalDateTime.now(), null);

        Product changed = product();
        String second = service.build(changed, "{\"engineeringRequirements\":\"新要求\"}", "V2.0", "管理员", LocalDateTime.now(), null).json();

        assertNotEquals(first.json(), second);
        assertTrue(first.json().contains("旧要求"), "旧快照字符串不随后续构建改变");
        assertFalse(second.contains("旧要求"));
        assertTrue(second.contains("新要求"));
    }
}
