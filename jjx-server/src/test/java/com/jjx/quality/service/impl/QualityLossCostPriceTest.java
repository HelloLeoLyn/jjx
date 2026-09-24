package com.jjx.quality.service.impl;

import com.jjx.quality.service.impl.QualityLossCostServiceImpl.PriceCandidate;
import com.jjx.quality.service.impl.QualityLossCostServiceImpl.PriceSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 损失核算的取价优先级单测 —— dev-20260924-010。
 *
 * <p>业务口径：材料单价按**批次优先**逐级回退 ——
 * ①批次入库单价 → ②库存批次成本 → ③最近采购价 → ④物料主数据价 → ⑤无价（计 0 并列出）。
 * 这条链决定"损失金额是否可追溯"，必须固化，不能让实现随手换个顺序。</p>
 */
class QualityLossCostPriceTest {

    private static PriceCandidate p(PriceSource s, String v) {
        return new PriceCandidate(s, v == null ? null : new BigDecimal(v));
    }

    @Test
    void batchInboundPriceWins() {
        assertEquals(new BigDecimal("5.00"), QualityLossCostServiceImpl.pickPrice(List.of(
                p(PriceSource.MATERIAL_MASTER, "9.99"),
                p(PriceSource.PURCHASE, "7.00"),
                p(PriceSource.STOCK_COST, "6.00"),
                p(PriceSource.BATCH_INBOUND, "5.00"))).price());
    }

    @Test
    void fallsBackInBusinessOrder() {
        assertEquals(PriceSource.STOCK_COST, QualityLossCostServiceImpl.pickPrice(List.of(
                p(PriceSource.MATERIAL_MASTER, "9.99"),
                p(PriceSource.STOCK_COST, "6.00"))).source());
        assertEquals(PriceSource.PURCHASE, QualityLossCostServiceImpl.pickPrice(List.of(
                p(PriceSource.MATERIAL_MASTER, "9.99"),
                p(PriceSource.PURCHASE, "7.00"))).source());
        assertEquals(PriceSource.MATERIAL_MASTER, QualityLossCostServiceImpl.pickPrice(List.of(
                p(PriceSource.MATERIAL_MASTER, "9.99"))).source());
    }

    @Test
    void zeroOrMissingPriceIsNotUsed() {
        assertEquals(PriceSource.PURCHASE, QualityLossCostServiceImpl.pickPrice(List.of(
                p(PriceSource.BATCH_INBOUND, "0"),
                p(PriceSource.STOCK_COST, null),
                p(PriceSource.PURCHASE, "7.00"))).source());
    }

    @Test
    void noPriceMeansNoneRatherThanZeroGuessed() {
        PriceCandidate none = QualityLossCostServiceImpl.pickPrice(List.of(
                p(PriceSource.BATCH_INBOUND, null), p(PriceSource.STOCK_COST, "0")));
        assertEquals(PriceSource.NONE, none.source());
        assertNull(none.price());
    }

    /**
     * 损耗率口径：`loss_rate` 存**百分数**（5 = 5%），含损耗用量 = 单耗 ×(1 + loss_rate/100)。
     * 与仓库既有实现一致（EngineeringBomServiceImpl:680 注释 / OrderMaterialReserveServiceImpl:266）。
     * 若误当小数（×(1+5)）会把用量放大 100 倍 —— 2026-09-24 实际踩过这个坑（010/027 各修一处）。
     */
    @Test
    void lossRateIsPercentageNotDecimal() {
        assertEquals(0, QualityLossCostServiceImpl
                .withLossRate(new BigDecimal("0.5"), new BigDecimal("5"))
                .compareTo(new BigDecimal("0.525")));
        assertEquals(0, QualityLossCostServiceImpl
                .withLossRate(new BigDecimal("2"), new BigDecimal("5"))
                .compareTo(new BigDecimal("2.10")));
        assertEquals(0, QualityLossCostServiceImpl
                .withLossRate(new BigDecimal("10"), null)
                .compareTo(new BigDecimal("10")));
    }
}
