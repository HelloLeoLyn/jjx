package com.jjx.quality.service.support;

import com.jjx.quality.domain.entity.QualityLot;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Distinguishes whole-lot revision links from partial rework-inspection links. */
public final class QualityLotLineagePolicy {

    private QualityLotLineagePolicy() {
    }

    public static boolean isReworkInspection(String lotType, Long executionId, Set<Long> reworkExecutionIds) {
        return "FQC".equalsIgnoreCase(lotType)
                && executionId != null
                && reworkExecutionIds != null
                && reworkExecutionIds.contains(executionId);
    }

    /**
     * Whole-lot reinspection children replace their parent. A rework inspection child only represents
     * the repaired subset, so it must be counted alongside the unchanged parent lot.
     */
    public static Set<Long> effectiveSupersededLotIds(List<QualityLot> lots, Set<Long> reworkExecutionIds) {
        if (lots == null || lots.isEmpty()) {
            return Set.of();
        }
        Set<Long> superseded = new HashSet<>();
        List<QualityLot> reworkChildren = new ArrayList<>();
        List<QualityLot> replacementChildren = new ArrayList<>();
        for (QualityLot lot : lots) {
            if (lot.getParentLotId() == null) {
                continue;
            }
            if (isReworkInspection(lot.getLotType(), lot.getExecutionId(), reworkExecutionIds)) {
                reworkChildren.add(lot);
            } else {
                replacementChildren.add(lot);
                superseded.add(lot.getParentLotId());
            }
        }
        // If a whole-lot revision was created after a partial rework lot, the revision supersedes that
        // earlier subset result too; otherwise both remain additive to the source lot.
        for (QualityLot reworkChild : reworkChildren) {
            boolean laterReplacement = replacementChildren.stream().anyMatch(replacement ->
                    reworkChild.getParentLotId().equals(replacement.getParentLotId())
                            && reworkChild.getLotId() != null && replacement.getLotId() != null
                            && replacement.getLotId() > reworkChild.getLotId());
            if (laterReplacement) {
                superseded.add(reworkChild.getLotId());
            }
        }
        return superseded;
    }
}
