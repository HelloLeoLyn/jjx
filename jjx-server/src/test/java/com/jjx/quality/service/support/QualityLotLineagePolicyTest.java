package com.jjx.quality.service.support;

import com.jjx.quality.domain.entity.QualityLot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QualityLotLineagePolicyTest {

    @Test
    void reworkInspectionChildDoesNotReplaceTheWholeSourceLot() {
        QualityLot source = lot(6L, null, null);
        QualityLot reworkSubLot = lot(7L, 6L, 5L);

        assertEquals(Set.of(), QualityLotLineagePolicy.effectiveSupersededLotIds(
                List.of(source, reworkSubLot), Set.of(5L)));
    }

    @Test
    void wholeLotReinspectionStillReplacesItsParentAndOlderReworkSubLot() {
        QualityLot source = lot(6L, null, null);
        QualityLot reworkSubLot = lot(7L, 6L, 5L);
        QualityLot wholeLotRevision = lot(8L, 6L, 9L);

        assertEquals(Set.of(6L, 7L), QualityLotLineagePolicy.effectiveSupersededLotIds(
                List.of(source, reworkSubLot, wholeLotRevision), Set.of(5L)));
    }

    private QualityLot lot(Long id, Long parentId, Long executionId) {
        QualityLot lot = new QualityLot();
        lot.setLotId(id);
        lot.setParentLotId(parentId);
        lot.setLotType("FQC");
        lot.setExecutionId(executionId);
        return lot;
    }
}
