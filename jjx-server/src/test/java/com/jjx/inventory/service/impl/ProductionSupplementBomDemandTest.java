package com.jjx.inventory.service.impl;

import com.jjx.engineering.domain.entity.EngineeringBomItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProductionSupplementBomDemandTest {

    @Test
    void supplementDemandUsesReplacementQuantityAndCombinesDuplicateMaterials() {
        EngineeringBomItem first = bomItem(1601L, "buy", "1.00");
        EngineeringBomItem duplicate = bomItem(1601L, "buy", "1.00");
        EngineeringBomItem manufactured = bomItem(1700L, "make", "2.00");
        manufactured.setSubBomId(99L);

        Map<Long, BigDecimal> demand = InventoryOutboundServiceImpl.aggregateSupplementDemand(
                List.of(first, duplicate, manufactured), new BigDecimal("8"));

        assertEquals(2, demand.size());
        assertEquals(new BigDecimal("16"), demand.get(1700L));
        assertEquals(new BigDecimal("16"), demand.get(1601L));
    }

    private static EngineeringBomItem bomItem(Long materialId, String sourceType, String actualIssueQty) {
        EngineeringBomItem item = new EngineeringBomItem();
        item.setMaterialId(materialId);
        item.setSourceType(sourceType);
        item.setUnit("PCS");
        item.setActualIssueQty(new BigDecimal(actualIssueQty));
        item.setLossRate(BigDecimal.ZERO);
        return item;
    }
}
