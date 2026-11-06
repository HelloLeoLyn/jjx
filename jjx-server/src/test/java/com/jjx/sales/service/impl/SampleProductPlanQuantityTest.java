package com.jjx.sales.service.impl;

import com.jjx.engineering.domain.entity.EngineeringBom;
import com.jjx.engineering.domain.entity.EngineeringBomItem;
import com.jjx.engineering.domain.entity.EngineeringRouting;
import com.jjx.product.mapper.EngineeringBomItemMapper;
import com.jjx.product.service.IEngineeringBomService;
import com.jjx.product.service.IEngineeringRoutingService;
import com.jjx.sales.domain.entity.SalesSampleOrder;
import com.jjx.sales.mapper.SalesSampleOrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 成品方案只读带出：保留源BOM基/模，空值不得由用量推导。 */
@ExtendWith(MockitoExtension.class)
class SampleProductPlanQuantityTest {
    @Mock SalesSampleOrderMapper sampleOrderMapper;
    @Mock IEngineeringBomService bomService;
    @Mock IEngineeringRoutingService routingService;
    @Mock EngineeringBomItemMapper bomItemMapper;
    @InjectMocks SampleOrderServiceImpl service;

    @BeforeEach
    void setup() {
        var profile = new SalesSampleOrder();
        profile.setProductId(20L);
        when(sampleOrderMapper.selectByOrderId(10L)).thenReturn(profile);
        var bom = new EngineeringBom();
        bom.setBomId(30L);
        bom.setApproveStatus(3);
        when(bomService.getOne(any())).thenReturn(bom);
        var routing = new EngineeringRouting();
        routing.setRoutingId(40L);
        routing.setApproveStatus(3);
        when(routingService.getOne(any())).thenReturn(routing);
    }

    private EngineeringBomItem item(String base, String module) {
        var item = new EngineeringBomItem();
        item.setMaterialName("基材");
        item.setQuantity(BigDecimal.ONE);
        item.setBaseQty(base == null ? null : new BigDecimal(base));
        item.setModuleQty(module == null ? null : new BigDecimal(module));
        return item;
    }

    @Test
    void productPlanKeepsExactBaseAndModuleEvenWhenQuantityDiffers() {
        when(bomItemMapper.selectList(any())).thenReturn(List.of(item("3", "4"), item("6", "8")));
        var plan = service.getProductPlan(10L);
        assertTrue(plan.getEligible());
        var rows = plan.getUnlinkedMaterials();
        assertEquals(new BigDecimal("3"), rows.get(0).get("baseQty"));
        assertEquals(new BigDecimal("4"), rows.get(0).get("moduleQty"));
        assertEquals(new BigDecimal("6"), rows.get(1).get("baseQty"));
        assertEquals(new BigDecimal("8"), rows.get(1).get("moduleQty"));
        verify(bomItemMapper, never()).insert(any(EngineeringBomItem.class));
    }

    @Test
    void productPlanIncludesExplicitNullsForMissingSourceQuantities() {
        when(bomItemMapper.selectList(any())).thenReturn(List.of(item(null, null), item("3", null), item(null, "4")));
        var rows = service.getProductPlan(10L).getUnlinkedMaterials();
        for (var row : rows) {
            assertTrue(row.containsKey("baseQty"));
            assertTrue(row.containsKey("moduleQty"));
        }
        assertNull(rows.get(0).get("baseQty"));
        assertNull(rows.get(0).get("moduleQty"));
        assertEquals(new BigDecimal("3"), rows.get(1).get("baseQty"));
        assertNull(rows.get(1).get("moduleQty"));
        assertNull(rows.get(2).get("baseQty"));
        assertEquals(new BigDecimal("4"), rows.get(2).get("moduleQty"));
    }
}
