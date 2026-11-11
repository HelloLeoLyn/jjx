package com.jjx.production.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.jjx.common.exception.BusinessException;
import com.jjx.production.domain.vo.ManufacturingSnapshotVO;

/** 工单制造历史快照（dev-20261111-009 G1）：只读组装与缺失降级。 */
class ManufacturingHistorySnapshotTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

    private ManufacturingHistorySnapshotServiceImpl service() {
        return new ManufacturingHistorySnapshotServiceImpl(jdbcTemplate);
    }

    private static Map<String, Object> row(Object... pairs) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return map;
    }

    private Map<String, Object> orderRow(Long routingId, Long specVersionId) {
        return row("order_id", 1L, "order_no", "WO-1", "output_type", "PRODUCT", "output_id", 2L,
                "product_code", "P002", "product_name", "成品", "planned_quantity", new BigDecimal("300"),
                "bom_id", 10L, "bom_code", "BOM-10", "routing_id", routingId, "work_spec_version_id", specVersionId);
    }

    @Test
    void depthOverLimitIsRejected() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service().snapshot(1L, 99));
        assertTrue(ex.getMessage().contains("BOM_DEPTH_EXCEEDED"));
    }

    @Test
    void missingOrderIsRejected() {
        when(jdbcTemplate.queryForList(anyString(), org.mockito.ArgumentMatchers.<Object>any())).thenReturn(List.of());
        assertThrows(BusinessException.class, () -> service().snapshot(1L, 32));
    }

    @Test
    void emptyBomMarksSnapshotPartial() {
        when(jdbcTemplate.queryForList(anyString(), org.mockito.ArgumentMatchers.<Object>any()))
                .thenReturn(List.of(orderRow(null, null)))
                .thenReturn(List.of());
        ManufacturingSnapshotVO vo = service().snapshot(1L, 32);
        assertEquals("SNAPSHOT_PARTIAL", vo.getSnapshotStatus());
        assertTrue(vo.getWarnings().stream().anyMatch(w -> w.contains("没有明细行") || w.contains("未绑定工艺路线")));
    }

    @Test
    void flatBomBuildsNodesAndCompleteStatus() {
        Map<String, Object> item = row("item_id", 1L, "bom_id", 10L, "material_id", 77L, "material_code", "M77",
                "material_name", "材料77", "unit", "PCS", "quantity", new BigDecimal("2"), "item_order", 1,
                "module_qty", new BigDecimal("1"), "base_qty", new BigDecimal("1"), "sub_bom_id", null);
        when(jdbcTemplate.queryForList(anyString(), org.mockito.ArgumentMatchers.<Object>any()))
                .thenReturn(List.of(orderRow(null, null)))
                .thenReturn(List.of(item))
                .thenReturn(List.of());
        ManufacturingSnapshotVO vo = service().snapshot(1L, 32);
        assertEquals(1, vo.getNodes().size());
        assertEquals("M77", vo.getNodes().get(0).getMaterialCode());
        assertEquals("BOM_ITEM", vo.getNodes().get(0).getSnapshotSource());
    }
}
