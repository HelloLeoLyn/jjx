package com.jjx.product.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.product.domain.entity.ProductWorkSpecItem;
import com.jjx.product.enums.WorkSpecResourceType;
import com.jjx.product.mapper.ProductWorkSpecItemMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * 工程图集收敛 A（dev-20261019-017）：快照文件条目携带 documentType；
 * 读取签名优先 documentType，旧快照（无该字段）回退 category/operationRef。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkSpecSnapshotDocumentTypeTest {

    @Mock ProductWorkSpecItemMapper itemMapper;
    @Spy ObjectMapper json = new ObjectMapper();
    @InjectMocks ProductWorkSpecVersionService service;

    private ProductWorkSpecItem drawing(String operationRef, String snapshotJson, long sourceId, String hash) {
        ProductWorkSpecItem item = new ProductWorkSpecItem();
        item.setResourceType(WorkSpecResourceType.DRAWING.getCode());
        item.setOperationRef(operationRef);
        item.setSnapshotJson(snapshotJson);
        item.setSourceId(sourceId);
        item.setContentHash(hash);
        return item;
    }

    @Test
    void snapshotFileCarriesDocumentTypeAndCategory() {
        var file = new WorkSpecDocsetSnapshotService.SnapshotFile(1L, "atlas", "结构图", "STRUCTURE_DRAWING",
                "a.jpg", null, null, null, "PRINT", "image/jpeg", 1L, "abc", "product-library", null, 0);
        assertEquals("STRUCTURE_DRAWING", file.documentType());
        assertEquals("结构图", file.category());
        var moved = file.withSection("sample");
        assertEquals("sample", moved.section());
        assertEquals("STRUCTURE_DRAWING", moved.documentType());
        assertEquals("结构图", moved.category());
    }

    @Test
    void drawingSignaturePrefersDocumentTypeAndFallsBackToCategory() throws Exception {
        when(itemMapper.selectByVersionId(1L)).thenReturn(List.of(
                drawing("结构图", "{\"attachmentId\":1,\"category\":\"结构图\",\"documentType\":\"STRUCTURE_DRAWING\"}", 1L, "h1"),
                // 旧快照：只有 category，没有 documentType
                drawing("外形尺寸图", "{\"attachmentId\":2,\"category\":\"外形尺寸图\"}", 2L, "h2")));

        Method m = ProductWorkSpecVersionService.class.getDeclaredMethod("drawingSignature", Long.class);
        m.setAccessible(true);
        String signature = (String) m.invoke(service, 1L);

        assertTrue(signature.contains("STRUCTURE_DRAWING|1|h1"), signature);
        assertTrue(signature.contains("外形尺寸图|2|h2"), signature);
    }
}
