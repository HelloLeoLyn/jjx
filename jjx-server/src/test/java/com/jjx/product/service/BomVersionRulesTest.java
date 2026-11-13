package com.jjx.product.service;

import com.jjx.common.exception.BusinessException;
import com.jjx.engineering.domain.entity.EngineeringBom;
import com.jjx.product.mapper.EngineeringBomMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** BOM 版本资格与跳距免模数规则（dev-20261111-019）。 */
class BomVersionRulesTest {

    private final EngineeringBomMapper bomMapper = mock(EngineeringBomMapper.class);
    private final BomVersionRules rules = new BomVersionRules(bomMapper);

    private EngineeringBom bom(long id, int approveStatus, boolean current, LocalDate effective, LocalDate expiry) {
        EngineeringBom bom = new EngineeringBom();
        bom.setBomId(id);
        bom.setProductId(1L);
        bom.setBomCode("B" + id);
        bom.setApproveStatus(approveStatus);
        bom.setIsCurrent(current);
        bom.setOutputType("PRODUCT");
        bom.setEffectiveDate(effective == null ? null : Date.from(effective.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()));
        bom.setExpiryDate(expiry == null ? null : Date.from(expiry.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()));
        return bom;
    }

    @Test
    void pitchRowIsExemptFromModuleAndBase() {
        assertTrue(BomVersionRules.pitchRowExemptFromRatio(new BigDecimal("80"), 1));
        assertTrue(BomVersionRules.pitchRowExemptFromRatio(new BigDecimal("80"), null));
        assertFalse(BomVersionRules.pitchRowExemptFromRatio(null, 1));
        assertFalse(BomVersionRules.pitchRowExemptFromRatio(BigDecimal.ZERO, 1));
    }

    @Test
    void eligibilityRequiresApprovedAndDateWindow() {
        LocalDate day = LocalDate.of(2026, 6, 1);
        Integer approved = 3;
        assertTrue(BomVersionRules.isEligibleLocal(approved, LocalDate.of(2026, 1, 1), null, day, approved));
        assertTrue(BomVersionRules.isEligibleLocal(approved, null, LocalDate.of(2026, 7, 1), day, approved));
        assertFalse(BomVersionRules.isEligibleLocal(2, LocalDate.of(2026, 1, 1), null, day, approved));
        assertFalse(BomVersionRules.isEligibleLocal(approved, LocalDate.of(2026, 7, 1), null, day, approved));
        assertFalse(BomVersionRules.isEligibleLocal(approved, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 5, 31), day, approved));
    }

    @Test
    void picksOnlyDateEligibleVersion() {
        LocalDate day = LocalDate.of(2026, 6, 1);
        when(bomMapper.selectList(any())).thenReturn(List.of(
                bom(10L, 3, true, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 1)),
                bom(11L, 3, false, LocalDate.of(2026, 5, 1), null),
                bom(12L, 2, true, LocalDate.of(2026, 1, 1), null)));
        assertEquals(11L, rules.resolveEffectiveVersion(1L, null, day).getBomId());
    }

    @Test
    void isCurrentOnlyBreaksTiesAndNeverOverridesDates() {
        LocalDate day = LocalDate.of(2026, 6, 1);
        when(bomMapper.selectList(any())).thenReturn(List.of(
                bom(20L, 3, true, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1)),
                bom(21L, 3, false, LocalDate.of(2026, 1, 1), null)));
        assertEquals(21L, rules.resolveEffectiveVersion(1L, null, day).getBomId());
    }

    @Test
    void expiredOrUnapprovedHasNoVersion() {
        when(bomMapper.selectList(any())).thenReturn(List.of(
                bom(30L, 3, true, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1)),
                bom(31L, 2, false, LocalDate.of(2026, 1, 1), null)));
        assertThrows(BusinessException.class, () -> rules.resolveEffectiveVersion(1L, null, LocalDate.of(2026, 6, 1)));
    }
}
