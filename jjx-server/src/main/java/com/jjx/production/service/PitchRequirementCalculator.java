package com.jjx.production.service;

import com.jjx.common.exception.BusinessException;
import com.jjx.production.domain.dto.PitchRequirementPreviewRequest;
import com.jjx.production.domain.model.BomConsumptionCalculator;
import com.jjx.production.domain.model.ExactQuantity;
import com.jjx.production.domain.vo.PitchRequirementPreviewVO;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 跳距辅助定额只读计算器（dev-20261112-004）。
 *
 * <p>口径：每跳投入 baseQty（M = 跳距/1000；M2 = 卷宽/1000 × 跳距/1000），
 * moduleQty = 每跳产出 PCS，耗用一律交给 {@link BomConsumptionCalculator#theoretical}
 * 这条既有权威链路，不另写第二套换算。</p>
 *
 * <p>边界：净长不参与计算（净长 80mm 但跳距 100mm 按 100mm 计）；只算节距耗用，
 * 不叠加损耗、不算整卷实发/最低投料、不写任何表；纯函数，重复调用结果一致。</p>
 */
public final class PitchRequirementCalculator {

    /** 默认产出数量（PCS）。 */
    public static final int DEFAULT_OUTPUT_PCS = 100;

    public static final String UNIT_M = "M";
    public static final String UNIT_M2 = "M2";

    /** 损耗口径说明（VO 必带）。 */
    public static final String LOSS_NOTE = "仅节距耗用，额外损耗沿BOM既有字段确认（本预览不叠加 lossRate）";

    /** 整卷实发口径说明（VO 必带）。 */
    public static final String ISSUE_NOTE = "整卷实发/最低投料不在本预览计算，本预览只给理论耗用";

    /** 展示值口径说明。 */
    public static final String DISPLAY_NOTE = "displayConsumption 为 4 位小数 HALF_UP 展示值，权威精确值为 exactConsumptionText";

    /** engineering_bom_item.width_mm 为 decimal(10,2)。 */
    private static final int WIDTH_MAX_SCALE = 2;

    /** engineering_bom_item.base_qty / module_qty 为 decimal(12,4)。 */
    private static final int BOM_QUANTITY_SCALE = 4;
    private static final int BOM_QUANTITY_INTEGER_DIGITS = 8;

    /** 精确值搜索允许的最大小数位（跳距整数 + 卷宽 2 位 → 精确值最多 5 位）。 */
    private static final int EXACT_SEARCH_MAX_SCALE = 8;

    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);

    private PitchRequirementCalculator() {
    }

    /**
     * 只读预览：不落库、无副作用。参数非法一律抛 {@link BusinessException}（可读中文）。
     */
    public static PitchRequirementPreviewVO preview(PitchRequirementPreviewRequest request) {
        if (request == null) {
            throw new BusinessException("跳距定额预览入参不能为空");
        }
        BigDecimal widthMm = requirePositiveDecimal(request.getWidthMm(), "卷宽 widthMm", WIDTH_MAX_SCALE);
        BigDecimal pitchMm = requirePositiveInteger(request.getPitchMm(), "跳距 pitchMm");
        BigDecimal pcsPerJump = requirePositiveInteger(request.getPcsPerJump(), "每跳产出 pcsPerJump");
        String inputUnit = requireInputUnit(request.getInputUnit());

        int outputPcs;
        boolean outputPcsDefaulted;
        if (request.getOutputPcs() == null) {
            outputPcs = DEFAULT_OUTPUT_PCS;
            outputPcsDefaulted = true;
        } else {
            outputPcs = requirePositiveInteger(request.getOutputPcs(), "产出数量 outputPcs").intValueExact();
            outputPcsDefaulted = false;
        }

        // 每跳投入：只用宽 × 跳距，净长不参与（净长小于跳距时仍按跳距计）。
        ExactQuantity pitchMeter = ExactQuantity.fromDecimal(pitchMm).divide(ExactQuantity.of(1000));
        ExactQuantity baseExact = UNIT_M.equals(inputUnit)
                ? pitchMeter
                : ExactQuantity.fromDecimal(widthMm).divide(ExactQuantity.of(1000)).multiply(pitchMeter);

        BigDecimal baseExactDecimal = exactAtScale(baseExact, EXACT_SEARCH_MAX_SCALE);
        boolean baseExactlyRepresentable = baseExactDecimal != null;
        // 理论不可达（跳距整数 + 卷宽 2 位小数必然是有限小数）；兜底保留近似值，不静默归零。
        BigDecimal baseForTheoretical = baseExactlyRepresentable ? baseExactDecimal : baseExact.approximate(12);

        BigDecimal baseNormalized = baseExactlyRepresentable ? normalize(baseExactDecimal) : null;
        boolean baseStorable = baseNormalized != null
                && Math.max(0, baseNormalized.scale()) <= BOM_QUANTITY_SCALE
                && integerDigits(baseNormalized) <= BOM_QUANTITY_INTEGER_DIGITS;

        BigDecimal moduleQty = normalize(pcsPerJump);

        // 权威链路：与制造定额同一套 theoretical(output, base, module, legacy)。
        ExactQuantity total = BomConsumptionCalculator.theoretical(
                BigDecimal.valueOf(outputPcs), baseForTheoretical, moduleQty, null);

        PitchRequirementPreviewVO vo = new PitchRequirementPreviewVO();
        vo.setWidthMm(widthMm);
        vo.setPitchMm(pitchMm);
        vo.setPcsPerJump(pcsPerJump);
        vo.setInputUnit(inputUnit);
        vo.setOutputPcs(outputPcs);
        vo.setOutputPcsDefaulted(outputPcsDefaulted);
        vo.setModuleQtySuggested(moduleQty);
        vo.setBaseQtySuggested(baseStorable ? baseNormalized : null);
        vo.setBaseQtyExactText(baseExact.exactText());
        vo.setBaseQtyStorableInBom(baseStorable);
        vo.setBaseQtyStorageNote(baseStorable ? null : storageNote(baseExact));
        vo.setExactConsumptionText(total.exactText());
        vo.setDisplayConsumption(total.toBigDecimal(BOM_QUANTITY_SCALE, RoundingMode.HALF_UP));
        vo.setUnit(inputUnit);
        vo.setLossIncluded(false);
        vo.setLossNote(LOSS_NOTE);
        vo.setActualIssueIncluded(false);
        vo.setIssueNote(ISSUE_NOTE);
        vo.setNotes(notes(baseStorable, baseExact));
        return vo;
    }

    private static List<String> notes(boolean baseStorable, ExactQuantity baseExact) {
        List<String> notes = new ArrayList<>();
        notes.add(LOSS_NOTE);
        notes.add(ISSUE_NOTE);
        notes.add(DISPLAY_NOTE);
        notes.add("耗用只按「卷宽 × 跳距 × 跳数」计；净长不参与，净长小于跳距时按跳距计");
        notes.add("moduleQty 建议 = 每跳产出 PCS；baseQty 建议 = 每跳投入量，回填需用户确认后走 BOM 保存入口");
        if (!baseStorable) {
            notes.add("baseQty 精确值为 " + baseExact.exactText()
                    + "，无法在 decimal(12,4) 内精确表达，不可直接落库；本预览不写库，需人工确认比例表达");
        }
        return notes;
    }

    private static String storageNote(ExactQuantity baseExact) {
        return "baseQty 精确值 " + baseExact.exactText()
                + " 无法在 decimal(12,4) 内精确表达，不可直接落库（未截断、未四舍五入）；本预览不写库";
    }

    /** >0 且最多 maxScale 位小数（按 stripTrailingZeros 后的有效精度判定，195.00 视为合法）。 */
    private static BigDecimal requirePositiveDecimal(BigDecimal value, String field, int maxScale) {
        if (value == null) {
            throw new BusinessException(field + " 不能为空");
        }
        if (value.signum() <= 0) {
            throw new BusinessException(field + " 必须大于 0（收到 " + value.toPlainString() + "）");
        }
        int actualScale = Math.max(0, value.stripTrailingZeros().scale());
        if (actualScale > maxScale) {
            throw new BusinessException(field + " 最多支持 " + maxScale + " 位小数，拒绝超精度（收到 "
                    + value.toPlainString() + "）");
        }
        if (integerDigits(value) > BOM_QUANTITY_INTEGER_DIGITS) {
            throw new BusinessException(field + " 整数位超出 decimal(12,4) 上限（收到 " + value.toPlainString() + "）");
        }
        return value.stripTrailingZeros().scale() < 0 ? value.setScale(0) : value.stripTrailingZeros();
    }

    /** >0 的正整数（跳距 mm、每跳产出 PCS、产出数量 PCS）。 */
    private static BigDecimal requirePositiveInteger(BigDecimal value, String field) {
        if (value == null) {
            throw new BusinessException(field + " 不能为空");
        }
        if (value.signum() <= 0) {
            throw new BusinessException(field + " 必须大于 0（收到 " + value.toPlainString() + "）");
        }
        if (Math.max(0, value.stripTrailingZeros().scale()) > 0) {
            throw new BusinessException(field + " 只支持整数，拒绝超精度（收到 " + value.toPlainString() + "）");
        }
        BigDecimal normalized = normalize(value);
        if (integerDigits(normalized) > BOM_QUANTITY_INTEGER_DIGITS) {
            throw new BusinessException(field + " 整数位超出 decimal(12,4) 上限（收到 " + value.toPlainString() + "）");
        }
        return normalized;
    }

    /** 只接受 M / M2（米、㎡ 等由既有 normalizeUnit 归一）。 */
    private static String requireInputUnit(String rawUnit) {
        if (rawUnit == null || rawUnit.isBlank()) {
            throw new BusinessException("投入单位 inputUnit 不能为空（仅支持 M 或 M2）");
        }
        String normalized = BomConsumptionCalculator.normalizeUnit(rawUnit);
        if (!UNIT_M.equals(normalized) && !UNIT_M2.equals(normalized)) {
            throw new BusinessException("投入单位 inputUnit 仅支持 M 或 M2（收到 " + rawUnit + "）");
        }
        return normalized;
    }

    /** 精确转换：0..maxScale 位内能精确表达就返回该值，否则 null（不做四舍五入）。 */
    private static BigDecimal exactAtScale(ExactQuantity quantity, int maxScale) {
        for (int scale = 0; scale <= maxScale; scale++) {
            try {
                return quantity.toBigDecimal(scale, RoundingMode.UNNECESSARY);
            } catch (ArithmeticException ignored) {
                // 需要舍入即说明该精度无法精确表达，继续加位。
            }
        }
        return null;
    }

    /** 去掉无意义末尾零，且不产生负 scale（避免 1E+2 这类展示）。 */
    private static BigDecimal normalize(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }

    private static int integerDigits(BigDecimal value) {
        BigDecimal normalized = normalize(value);
        return normalized.precision() - normalized.scale();
    }
}
