package com.jjx.production.service;

import com.jjx.common.exception.BusinessException;
import com.jjx.production.domain.dto.PitchRequirementPreviewRequest;
import com.jjx.production.domain.model.BomConsumptionCalculator;
import com.jjx.production.domain.vo.PitchRequirementPreviewVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 跳距辅助定额只读计算器（dev-20261112-004）。
 *
 * <p>只测纯计算器：不启动 Spring、不连库、不写文件。</p>
 */
class PitchRequirementCalculatorTest {

    private static PitchRequirementPreviewRequest request(String widthMm, String pitchMm,
                                                          String pcsPerJump, String inputUnit, String outputPcs) {
        PitchRequirementPreviewRequest request = new PitchRequirementPreviewRequest();
        request.setWidthMm(widthMm == null ? null : new BigDecimal(widthMm));
        request.setPitchMm(pitchMm == null ? null : new BigDecimal(pitchMm));
        request.setPcsPerJump(pcsPerJump == null ? null : new BigDecimal(pcsPerJump));
        request.setInputUnit(inputUnit);
        request.setOutputPcs(outputPcs == null ? null : new BigDecimal(outputPcs));
        return request;
    }

    private static void assertDecimal(String expected, BigDecimal actual) {
        assertNotNull(actual, "期望 " + expected + "，实际为 null");
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                "期望 " + expected + "，实际 " + actual.toPlainString());
    }

    @Test
    @DisplayName("未传 outputPcs 默认 100，与显式传 100 结果一致且回显默认标记")
    void defaultOutputPcsIsHundredAndMatchesExplicitHundred() {
        PitchRequirementPreviewVO byDefault = PitchRequirementCalculator.preview(
                request("195", "80", "1", "M2", null));
        PitchRequirementPreviewVO explicit = PitchRequirementCalculator.preview(
                request("195", "80", "1", "M2", "100"));

        assertEquals(100, byDefault.getOutputPcs());
        assertTrue(byDefault.isOutputPcsDefaulted());
        assertEquals(100, explicit.getOutputPcs());
        assertFalse(explicit.isOutputPcsDefaulted());
        assertEquals(byDefault.getExactConsumptionText(), explicit.getExactConsumptionText());
        assertDecimal("1.56", byDefault.getDisplayConsumption());
        assertDecimal("1.56", explicit.getDisplayConsumption());
    }

    @Test
    @DisplayName("195mm宽/80mm跳/1PCS每跳：M 得 8M，M2 得 1.56M2")
    void onePiecePerJumpYieldsEightMetersOrOnePointFiveSixSquareMeters() {
        PitchRequirementPreviewVO meter = PitchRequirementCalculator.preview(
                request("195", "80", "1", "M", null));
        assertEquals("M", meter.getUnit());
        assertDecimal("8", meter.getDisplayConsumption());
        assertDecimal("0.08", meter.getBaseQtySuggested());
        assertDecimal("1", meter.getModuleQtySuggested());
        assertTrue(meter.isBaseQtyStorableInBom());

        PitchRequirementPreviewVO square = PitchRequirementCalculator.preview(
                request("195", "80", "1", "M2", null));
        assertEquals("M2", square.getUnit());
        assertDecimal("1.56", square.getDisplayConsumption());
        assertDecimal("0.0156", square.getBaseQtySuggested());
        assertTrue(square.isBaseQtyStorableInBom());
    }

    @Test
    @DisplayName("195mm宽/80mm跳/2PCS每跳：M 得 4M，M2 得 0.78M2")
    void twoPiecesPerJumpHalvesTheRequirement() {
        PitchRequirementPreviewVO meter = PitchRequirementCalculator.preview(
                request("195", "80", "2", "M", null));
        assertDecimal("4", meter.getDisplayConsumption());
        assertDecimal("2", meter.getModuleQtySuggested());

        PitchRequirementPreviewVO square = PitchRequirementCalculator.preview(
                request("195", "80", "2", "M2", null));
        assertDecimal("0.78", square.getDisplayConsumption());
        assertDecimal("2", square.getModuleQtySuggested());
    }

    @Test
    @DisplayName("净长 80mm 但跳距 100mm 按跳距 100mm 计，不按净片材面积")
    void netLengthIsIgnoredAndPitchWins() {
        PitchRequirementPreviewVO meter = PitchRequirementCalculator.preview(
                request("195", "100", "1", "M", null));
        assertDecimal("10", meter.getDisplayConsumption());
        assertDecimal("0.1", meter.getBaseQtySuggested());

        PitchRequirementPreviewVO square = PitchRequirementCalculator.preview(
                request("195", "100", "1", "M2", null));
        assertDecimal("1.95", square.getDisplayConsumption());
        assertTrue(square.getNotes().stream().anyMatch(note -> note.contains("净长")),
                "口径说明必须写清净长不参与计算");
    }

    @Test
    @DisplayName("空/零/负 的 width/pitch/pcsPerJump/outputPcs 一律业务异常")
    void rejectsNullOrNonPositiveInputs() {
        assertThrows(BusinessException.class, () -> PitchRequirementCalculator.preview(
                request(null, "80", "1", "M", null)));
        assertThrows(BusinessException.class, () -> PitchRequirementCalculator.preview(
                request("0", "80", "1", "M", null)));
        assertThrows(BusinessException.class, () -> PitchRequirementCalculator.preview(
                request("-195", "80", "1", "M", null)));
        assertThrows(BusinessException.class, () -> PitchRequirementCalculator.preview(
                request("195", null, "1", "M", null)));
        assertThrows(BusinessException.class, () -> PitchRequirementCalculator.preview(
                request("195", "0", "1", "M", null)));
        assertThrows(BusinessException.class, () -> PitchRequirementCalculator.preview(
                request("195", "-80", "1", "M", null)));
        assertThrows(BusinessException.class, () -> PitchRequirementCalculator.preview(
                request("195", "80", null, "M", null)));
        assertThrows(BusinessException.class, () -> PitchRequirementCalculator.preview(
                request("195", "80", "0", "M", null)));
        assertThrows(BusinessException.class, () -> PitchRequirementCalculator.preview(
                request("195", "80", "-1", "M", null)));
        assertThrows(BusinessException.class, () -> PitchRequirementCalculator.preview(
                request("195", "80", "1", "M", "0")));
        assertThrows(BusinessException.class, () -> PitchRequirementCalculator.preview(
                request("195", "80", "1", "M", "-100")));
    }

    @Test
    @DisplayName("未知或空单位拒绝；米/㎡ 归一为 M/M2")
    void rejectsUnknownUnitAndNormalizesAliases() {
        assertThrows(BusinessException.class, () -> PitchRequirementCalculator.preview(
                request("195", "80", "1", "KG", null)));
        assertThrows(BusinessException.class, () -> PitchRequirementCalculator.preview(
                request("195", "80", "1", "M3", null)));
        assertThrows(BusinessException.class, () -> PitchRequirementCalculator.preview(
                request("195", "80", "1", null, null)));
        assertThrows(BusinessException.class, () -> PitchRequirementCalculator.preview(
                request("195", "80", "1", "  ", null)));

        assertEquals("M", PitchRequirementCalculator.preview(
                request("195", "80", "1", "米", null)).getInputUnit());
        assertEquals("M2", PitchRequirementCalculator.preview(
                request("195", "80", "1", "㎡", null)).getInputUnit());
    }

    @Test
    @DisplayName("卷宽 3 位小数、跳距 1.5 超精度一律拒绝，不静默截断")
    void rejectsOverPrecisionInputs() {
        assertThrows(BusinessException.class, () -> PitchRequirementCalculator.preview(
                request("195.123", "80", "1", "M2", null)));
        assertThrows(BusinessException.class, () -> PitchRequirementCalculator.preview(
                request("195", "1.5", "1", "M", null)));
        assertThrows(BusinessException.class, () -> PitchRequirementCalculator.preview(
                request("195", "80", "1.5", "M", null)));
        // 卷宽尾部零不算超精度（195.00 = 195）
        assertDecimal("1.56", PitchRequirementCalculator.preview(
                request("195.00", "80", "1", "M2", null)).getDisplayConsumption());
    }

    @Test
    @DisplayName("与 BomConsumptionCalculator.theoretical 结果一致（复用同一权威链路）")
    void consumptionMatchesBomTheoreticalContract() {
        PitchRequirementPreviewVO vo = PitchRequirementCalculator.preview(
                request("195", "80", "1", "M2", "100"));
        BigDecimal base = vo.getBaseQtySuggested();
        BigDecimal expected = BomConsumptionCalculator
                .theoretical(BigDecimal.valueOf(vo.getOutputPcs()), base, vo.getModuleQtySuggested(), null)
                .toBigDecimal(4, RoundingMode.HALF_UP);
        assertDecimal(expected.toPlainString(), vo.getDisplayConsumption());
        assertEquals("39/25", vo.getExactConsumptionText());
    }

    @Test
    @DisplayName("baseQty 无法在 decimal(12,4) 精确表达时标记不可落库并给精确文本")
    void marksBaseQtyNotStorableWhenBeyondFourDecimals() {
        // 195.5 / 1000 × 80 / 1000 = 0.01564，5 位小数，不能原值落 decimal(12,4)
        PitchRequirementPreviewVO vo = PitchRequirementCalculator.preview(
                request("195.5", "80", "1", "M2", null));
        assertFalse(vo.isBaseQtyStorableInBom());
        assertNull(vo.getBaseQtySuggested());
        assertNotNull(vo.getBaseQtyStorageNote());
        assertTrue(vo.getBaseQtyStorageNote().contains("不可直接落库"));
        assertEquals("391/25000", vo.getBaseQtyExactText());
        assertDecimal("1.564", vo.getDisplayConsumption());
    }

    @Test
    @DisplayName("连续两次预览结果完全相等（纯函数，无写入）")
    void repeatedPreviewIsPureAndEqual() {
        PitchRequirementPreviewVO first = PitchRequirementCalculator.preview(
                request("195", "80", "1", "M2", "100"));
        PitchRequirementPreviewVO second = PitchRequirementCalculator.preview(
                request("195", "80", "1", "M2", "100"));
        assertEquals(first, second, "同一入参两次结果必须相等，纯函数无状态无写入");
    }

    @Test
    @DisplayName("不叠加损耗：lossIncluded=false 且带损耗说明，耗用等于未加损耗的理论值")
    void lossIsNotAppliedInPreview() {
        PitchRequirementPreviewVO vo = PitchRequirementCalculator.preview(
                request("195", "80", "1", "M", null));
        assertFalse(vo.isLossIncluded());
        assertFalse(vo.isActualIssueIncluded());
        assertTrue(vo.getLossNote().contains("仅节距耗用"));
        assertTrue(vo.getLossNote().contains("额外损耗沿BOM既有字段确认"));
        assertTrue(vo.getIssueNote().contains("整卷实发"));
        // 100 PCS × 0.08M = 8M，未乘 (1+损耗)
        assertDecimal("8", vo.getDisplayConsumption());
        assertEquals("8/1", vo.getExactConsumptionText());
    }
}
