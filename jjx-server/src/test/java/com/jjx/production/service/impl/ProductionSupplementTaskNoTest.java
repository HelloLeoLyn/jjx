package com.jjx.production.service.impl;

import com.jjx.production.enums.ProductionTaskTypeEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * 补产任务号生成单测 —— dev-20260924-002（剩余半张：报废补产闭环）。
 *
 * <p>补产挂老工单、不另开单；任务号与普通任务同构，号位用 S 区分：
 * WO-&lt;工单号&gt;-P&lt;2位工序序&gt;-S&lt;2位流水&gt;（普通任务号位为 T）。
 * 两种号位共用同一工序流水（task_seq 递增），因此不会互相撞号。</p>
 */
class ProductionSupplementTaskNoTest {

    @Test
    void buildsSupplementTaskNoWithSPosition() {
        assertEquals("WO260923002-P03-S01", ProductionTaskServiceImpl.supplementTaskNo("WO260923002", 3, 1L));
    }

    @Test
    void padsProcessOrderAndSeq() {
        assertEquals("WO-X-P01-S02", ProductionTaskServiceImpl.supplementTaskNo("WO-X", 1, 2L));
        assertEquals("WO-X-P12-S10", ProductionTaskServiceImpl.supplementTaskNo("WO-X", 12, 10L));
    }

    @Test
    void fallsBackWhenOrderNoMissing() {
        assertEquals("SUPP-P03-S01", ProductionTaskServiceImpl.supplementTaskNo(null, 3, 1L));
        assertEquals("SUPP-P00-S01", ProductionTaskServiceImpl.supplementTaskNo("  ", null, 1L));
    }

    @Test
    void distinguishesFromStandardTaskNo() {
        assertEquals("STANDARD", ProductionTaskTypeEnum.STANDARD.getCode());
        assertEquals("SUPPLEMENT", ProductionTaskTypeEnum.SUPPLEMENT.getCode());
        assertNotEquals(ProductionTaskServiceImpl.supplementTaskNo("WO-X", 3, 1L), "WO-X-P03-T01");
    }
}
