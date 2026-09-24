package com.jjx.quality.service.impl;

import com.jjx.quality.domain.entity.QualityNcr;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 成品报废口径的适用范围单测 —— dev-20260924-028。
 *
 * <p>背景：dev-20260924-005/006 的「超阈值待审批」与「成品报废单」写在 IQC 处置也会走的
 * disposeInternal 里，导致来料报废多出一张成品报废单（SCR-）并与来料自己的 IQS 单/审批重复。
 * 修法：按批类型收窄 —— **IQC 不套成品口径**（来料走 IQS 单与自己的审批链）。</p>
 */
class QualityScrapGovernanceTest {

    @Test
    void iqcDoesNotFollowFqcScrapGovernance() {
        assertFalse(QualityNcrServiceImpl.scrapGovernanceApplies(ncr("IQC")));
        assertFalse(QualityNcrServiceImpl.scrapGovernanceApplies(ncr("iqc")));
    }

    @Test
    void fqcAndOqcFollowFqcScrapGovernance() {
        assertTrue(QualityNcrServiceImpl.scrapGovernanceApplies(ncr("FQC")));
        assertTrue(QualityNcrServiceImpl.scrapGovernanceApplies(ncr("OQC")));
    }

    @Test
    void missingLotTypeKeepsLegacyBehaviour() {
        assertTrue(QualityNcrServiceImpl.scrapGovernanceApplies(ncr(null)));
        assertFalse(QualityNcrServiceImpl.scrapGovernanceApplies(null));
    }

    private static QualityNcr ncr(String lotType) {
        QualityNcr ncr = new QualityNcr();
        ncr.setNcrId(1L);
        ncr.setLotType(lotType);
        return ncr;
    }
}
