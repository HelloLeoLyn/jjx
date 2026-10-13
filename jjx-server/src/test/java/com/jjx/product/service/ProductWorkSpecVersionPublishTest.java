package com.jjx.product.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.product.domain.entity.Product;
import com.jjx.product.domain.entity.ProductWorkSpecItem;
import com.jjx.product.domain.entity.ProductWorkSpecVersion;
import com.jjx.product.mapper.ProductMapper;
import com.jjx.product.mapper.ProductWorkSpecItemMapper;
import com.jjx.product.mapper.ProductWorkSpecMapper;
import com.jjx.product.mapper.ProductWorkSpecVersionMapper;
import com.jjx.system.mapper.SysAttachmentMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 发布链的原子性与条目组成（dev-20261013-010）：
 * 完整快照随发布写入；快照构建失败时不留任何可见半版本。
 */
@ExtendWith(MockitoExtension.class)
class ProductWorkSpecVersionPublishTest {

    @Mock ProductWorkSpecMapper workSpecMapper;
    @Mock ProductMapper productMapper;
    @Mock SysAttachmentMapper attachmentMapper;
    @Mock ProductWorkSpecVersionMapper versionMapper;
    @Mock ProductWorkSpecItemMapper itemMapper;
    @Mock WorkSpecDocsetSnapshotService docsetSnapshotService;

    ProductWorkSpecVersionService service;

    @BeforeEach
    void setUp() {
        service = new ProductWorkSpecVersionService(workSpecMapper, productMapper, attachmentMapper,
                versionMapper, itemMapper, docsetSnapshotService, new ObjectMapper());
    }

    private Product product() {
        Product product = new Product();
        product.setProductId(3L);
        product.setProductCode("YL1025PWOO");
        product.setProductName("面膜异形提手");
        product.setCurrentBomId(1L);
        product.setCurrentBomVersion("V1.0");
        return product;
    }

    private void stubBase() {
        Map<String, Object> lock = new HashMap<>();
        lock.put("product_id", 3L);
        when(workSpecMapper.lock(3L)).thenReturn(lock);
        when(productMapper.selectById(3L)).thenReturn(product());
        when(workSpecMapper.read(3L)).thenReturn("{}");
        when(versionMapper.selectCount(any())).thenReturn(0L);
        when(attachmentMapper.selectList(any())).thenReturn(List.of());
    }

    @Test
    void publishPersistsFullSnapshotAndRetentionRows() {
        stubBase();
        var snapshot = new WorkSpecDocsetSnapshotService.DocsetSnapshot(
                "{\"schemaVersion\":1}", "manifest-hash",
                List.of(new WorkSpecDocsetSnapshotService.SnapshotFile(40L, "atlas", "结构图", "a.jpg", null,
                        null, null, "PRINT", "image/jpeg", 1L, "abc", "product-library", null, 0)),
                0, 0);
        when(docsetSnapshotService.build(any(), any(), anyString(), any(), any(), any())).thenReturn(snapshot);

        ProductWorkSpecVersion version = service.publish(3L, "新增印刷工艺");

        assertEquals("V1.0", version.getVersionNo());
        verify(versionMapper, times(1)).insert(any(ProductWorkSpecVersion.class));

        ArgumentCaptor<ProductWorkSpecItem> captor = ArgumentCaptor.forClass(ProductWorkSpecItem.class);
        verify(itemMapper, times(4)).insert(captor.capture());
        List<String> types = captor.getAllValues().stream().map(ProductWorkSpecItem::getResourceType).toList();
        assertTrue(types.contains("SPEC_JSON"));
        assertTrue(types.contains("BOM"));
        assertTrue(types.contains("DOCSET_FILE"));
        assertTrue(types.contains("DOCSET_JSON"));
        // 快照与文件保留条目都绑定到同一版本
        ProductWorkSpecItem docset = captor.getAllValues().stream()
                .filter(i -> "DOCSET_JSON".equals(i.getResourceType())).findFirst().orElseThrow();
        assertEquals("manifest-hash", docset.getContentHash());
        assertTrue(docset.getSnapshotJson().contains("schemaVersion"));
        ProductWorkSpecItem retention = captor.getAllValues().stream()
                .filter(i -> "DOCSET_FILE".equals(i.getResourceType())).findFirst().orElseThrow();
        assertEquals(40L, retention.getSourceId());
    }

    @Test
    void publishFailsAtomicallyWhenSnapshotBuildFails() {
        stubBase();
        when(docsetSnapshotService.build(any(), any(), anyString(), any(), any(), any()))
                .thenThrow(new BusinessException("发布失败：附件文件缺失（id=40）"));

        assertThrows(BusinessException.class, () -> service.publish(3L, null));
        verify(versionMapper, never()).insert(any(ProductWorkSpecVersion.class));
        verify(itemMapper, never()).insert(any(ProductWorkSpecItem.class));
    }
}
