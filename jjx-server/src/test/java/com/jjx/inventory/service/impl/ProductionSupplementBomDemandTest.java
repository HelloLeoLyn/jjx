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
        EngineeringBomItem nonPurchased = bomItem(1700L, "make", "2.00");

        Map<Long, BigDecimal> demand = InventoryOutboundServiceImpl.aggregateSupplementDemand(
                List.of(first, duplicate, nonPurchased), new BigDecimal("8"));

        assertEquals(1, demand.size());
        assertEquals(new BigDecimal("16"), demand.get(1601L));
    }

    private static EngineeringBomItem bomItem(Long materialId, String sourceType, String actualIssueQty) {
        EngineeringBomItem item = new EngineeringBomItem();
        item.setMaterialId(materialId);
        item.setSourceType(sourceType);
        item.setActualIssueQty(new BigDecimal(actualIssueQty));
        item.setLossRate(0);
        return item;
    }
}
