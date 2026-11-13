package com.jjx.inventory.service.impl;

import com.jjx.engineering.domain.entity.EngineeringBomItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 缺料多层展开（dev-20261113-009）：递归、防环、只取整一次。 */
class ShortageExplosionTest {

    private EngineeringBomItem item(long materialId, String type, Long subBomId, String unitQty) {
        EngineeringBomItem row = new EngineeringBomItem();
        row.setMaterialId(materialId);
        row.setSourceType(type);
        row.setSubBomId(subBomId);
        row.setActualIssueQty(new BigDecimal(unitQty));
        return row;
    }

    @Test
    void selfMadeExpandsToParentMaterial() {
        Map<Long, List<EngineeringBomItem>> subs = new HashMap<>();
        subs.put(1L, List.of(item(351L, "buy", null, "0.0156")));
        Map<Long, BigDecimal> out = new HashMap<>();
        ShortageExplosion.explode(List.of(item(351L, "make", 1L, "1")), new BigDecimal("100"), 0,
                ShortageExplosion.newVisited(), subs::get, id -> true, out);
        assertEquals(0, new BigDecimal("1.56").compareTo(out.get(351L)));
    }

    @Test
    void cycleStopsAndFallsBackToCurrentItem() {
        Map<Long, List<EngineeringBomItem>> subs = new HashMap<>();
        subs.put(1L, List.of(item(351L, "make", 1L, "1")));
        Map<Long, BigDecimal> out = new HashMap<>();
        ShortageExplosion.explode(List.of(item(351L, "make", 1L, "1")), new BigDecimal("10"), 0,
                ShortageExplosion.newVisited(), subs::get, id -> true, out);
        assertEquals(0, new BigDecimal("10").compareTo(out.get(351L)));
    }

    @Test
    void depthOverFiveStops() {
        Map<Long, List<EngineeringBomItem>> subs = new HashMap<>();
        for (long i = 1; i <= 6; i++) {
            subs.put(i, List.of(item(100L + i, "make", i + 1, "1")));
        }
        Map<Long, BigDecimal> out = new HashMap<>();
        ShortageExplosion.explode(List.of(item(100L, "make", 1L, "1")), BigDecimal.ONE, 0,
                ShortageExplosion.newVisited(), subs::get, id -> true, out);
        assertEquals(false, out.isEmpty());
    }

    @Test
    void roundsOnlyOncePerMaterial() {
        Map<Long, BigDecimal> exact = Map.of(1L, new BigDecimal("17.17"), 2L, new BigDecimal("0.9"));
        Map<Long, BigDecimal> rounded = ShortageExplosion.roundOnce(exact);
        assertEquals(0, new BigDecimal("18").compareTo(rounded.get(1L)));
        assertEquals(0, BigDecimal.ONE.compareTo(rounded.get(2L)));
    }
}
