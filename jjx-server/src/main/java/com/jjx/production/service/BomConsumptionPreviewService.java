package com.jjx.production.service;

import com.jjx.common.exception.BusinessException;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.mapper.InventoryMaterialMapper;
import com.jjx.production.domain.dto.BomConsumptionPreviewRequest;
import com.jjx.production.domain.model.BomConsumptionCalculator;
import com.jjx.production.domain.model.ExactQuantity;
import com.jjx.production.domain.vo.BomConsumptionPreviewVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * BOM 明细耗用只读试算（dev-20261112-007）。
 *
 * <p>只读：只查物料档案（宽/长/基本单位），不写 BOM、不写库存、不产生流水；
 * 重复调用结果一致。耗用一律交给 {@link BomConsumptionCalculator#theoretical} /
 * {@link BomConsumptionCalculator#appliedDemand} / {@link BomConsumptionCalculator#roundStock}
 * 这条既有权威链路，本类不另写第二套换算，也不复制取整策略。</p>
 *
 * <p>失败边界：产出数量 Q、行数、key 属整请求问题，抛 {@link BusinessException} 由全局异常处理；
 * 比例/损耗/最低投料/单位/物料存在性属行级问题，只把该行置 valid=false 并给原因，
 * 其余行照常计算，绝不因单行让整表失败，也不把异常当 0。</p>
 *
 * <p>单位口径：计算单位始终取请求行 unit（旧 BOM 行单位保留），空时用档案 unit 兜底；
 * 档案 unit 只作为 archiveUnit 展示，不覆盖试算单位，不自动改写 BOM 或档案。</p>
 *
 * <p>参考米数/参考卷数只是展示参考：不库存、不预留、不当实发，也不代表物理卷或批次余量。</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BomConsumptionPreviewService {

    /** 单次试算最大明细行数。 */
    private static final int MAX_ROWS = 500;

    /** key 最大长度。 */
    private static final int KEY_MAX_LENGTH = 64;

    /** 产出数量上限（decimal(12,4) 之外的展示护栏，Q 不落库但避免无意义的巨值运算）。 */
    private static final BigDecimal MAX_OUTPUT_QUANTITY = new BigDecimal("99999999");

    /** 产出数量最多 4 位小数。 */
    private static final int OUTPUT_MAX_SCALE = 4;

    /** engineering_bom_item.base_qty / module_qty 为 decimal(12,4)。 */
    private static final int BOM_QUANTITY_SCALE = 4;
    private static final int BOM_QUANTITY_INTEGER_DIGITS = 8;

    /** 损耗率百分比最多 2 位小数。 */
    private static final int LOSS_MAX_SCALE = 2;

    /** 单件净耗与参考米数的展示精度。 */
    private static final int DISPLAY_SCALE = 4;

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private static final String UNIT_M = "M";
    private static final String UNIT_M2 = "M2";
    private static final String DEFAULT_OUTPUT_UNIT = "PCS";

    private final InventoryMaterialMapper inventoryMaterialMapper;

    /**
     * 只读试算：整请求校验 → 批量只读物料档案 → 逐行试算（行级异常隔离）。
     */
    public BomConsumptionPreviewVO preview(BomConsumptionPreviewRequest request) {
        if (request == null) {
            throw new BusinessException("耗用试算入参不能为空");
        }
        BigDecimal outputQuantity = requireOutputQuantity(request.getOutputQuantity());
        String outputUnit = resolveOutputUnit(request.getOutputUnit());

        BomConsumptionPreviewVO vo = new BomConsumptionPreviewVO();
        vo.setReadOnly(true);
        List<BomConsumptionPreviewVO.Row> resultRows = new ArrayList<>();
        vo.setRows(resultRows);

        List<BomConsumptionPreviewRequest.Row> rows = request.getRows();
        if (rows == null || rows.isEmpty()) {
            return vo;
        }
        if (rows.size() > MAX_ROWS) {
            throw new BusinessException("明细行数最多 " + MAX_ROWS + " 行，拒绝试算（收到 " + rows.size() + " 行）");
        }
        validateKeys(rows);

        Map<Long, InventoryMaterial> materials = loadMaterials(rows);
        for (BomConsumptionPreviewRequest.Row row : rows) {
            resultRows.add(previewRow(row, outputQuantity, outputUnit, materials));
        }
        return vo;
    }

    /** 产出数量：>0、≤99999999、stripTrailingZeros 后最多 4 位小数。 */
    private static BigDecimal requireOutputQuantity(BigDecimal value) {
        String field = "产出数量";
        if (value == null) {
            throw new BusinessException(field + " 不能为空");
        }
        if (value.signum() <= 0) {
            throw new BusinessException(field + " 必须大于 0（收到 " + value.toEngineeringString() + "）");
        }
        if (maxScale(value) > OUTPUT_MAX_SCALE) {
            throw new BusinessException(field + " 最多支持 " + OUTPUT_MAX_SCALE
                    + " 位小数，拒绝超精度（收到 " + value.toEngineeringString() + "）");
        }
        if (value.compareTo(MAX_OUTPUT_QUANTITY) > 0) {
            throw new BusinessException(field + " 超出可计算范围：最大 " + MAX_OUTPUT_QUANTITY.toPlainString()
                    + "（收到 " + value.toEngineeringString() + "）");
        }
        return value;
    }

    /** 产出单位：空/缺省按 PCS；只用于判断跳距接口可用性。 */
    private static String resolveOutputUnit(String outputUnit) {
        String normalized = BomConsumptionCalculator.normalizeUnit(outputUnit);
        return normalized.isBlank() ? DEFAULT_OUTPUT_UNIT : normalized;
    }

    /** key 必填、非空、≤64、同请求内唯一（大小写敏感、按原字符串比较）。 */
    private static void validateKeys(List<BomConsumptionPreviewRequest.Row> rows) {
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < rows.size(); index++) {
            int lineNo = index + 1;
            BomConsumptionPreviewRequest.Row row = rows.get(index);
            if (row == null) {
                throw new BusinessException("第 " + lineNo + " 行明细不能为空");
            }
            String key = row.getKey();
            if (key == null || key.isBlank()) {
                throw new BusinessException("第 " + lineNo + " 行 key 不能为空");
            }
            if (key.length() > KEY_MAX_LENGTH) {
                throw new BusinessException("第 " + lineNo + " 行 key 长度不能超过 " + KEY_MAX_LENGTH
                        + " 个字符（收到 " + key.length() + " 个）");
            }
            if (!seen.add(key)) {
                throw new BusinessException("第 " + lineNo + " 行 key[" + key + "] 重复，同一请求内 key 必须唯一");
            }
        }
    }

    /** 一次批量只读物档案；无 materialId 的行不查库、不猜任何尺寸。 */
    private Map<Long, InventoryMaterial> loadMaterials(List<BomConsumptionPreviewRequest.Row> rows) {
        Set<Long> ids = new LinkedHashSet<>();
        for (BomConsumptionPreviewRequest.Row row : rows) {
            if (row != null && row.getMaterialId() != null) {
                ids.add(row.getMaterialId());
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<InventoryMaterial> found = inventoryMaterialMapper.selectBatchIds(ids);
        Map<Long, InventoryMaterial> materials = new HashMap<>();
        if (found != null) {
            for (InventoryMaterial material : found) {
                if (material != null && material.getMaterialId() != null) {
                    materials.put(material.getMaterialId(), material);
                }
            }
        }
        return materials;
    }

    /** 单行试算；任何行级问题只置该行 invalid，不向外抛。 */
    private BomConsumptionPreviewVO.Row previewRow(BomConsumptionPreviewRequest.Row row, BigDecimal outputQuantity,
            String outputUnit, Map<Long, InventoryMaterial> materials) {
        BomConsumptionPreviewVO.Row out = new BomConsumptionPreviewVO.Row();
        out.setKey(row.getKey());

        InventoryMaterial material = row.getMaterialId() == null ? null : materials.get(row.getMaterialId());
        if (row.getMaterialId() != null && material == null) {
            out.setMessage("物料[" + row.getMaterialId() + "]不存在或已删除，不能试算");
            return out;
        }

        BigDecimal widthMm = material == null ? null : material.getWidthMm();
        BigDecimal lengthMm = material == null ? null : material.getLengthMm();
        out.setArchiveUnit(material == null ? null : normalizeText(material.getUnit()));
        out.setWidthMm(widthMm);
        out.setLengthMm(lengthMm);

        // 计算单位：请求行 unit 优先（旧 BOM 行单位保留），空才用档案单位兜底。
        String requestUnit = normalizeText(row.getUnit());
        String rawUnit = requestUnit != null ? requestUnit : out.getArchiveUnit();
        String unit = BomConsumptionCalculator.normalizeUnit(rawUnit);
        out.setUnit(unit.isBlank() ? null : unit);
        if (unit.isBlank()) {
            out.setMessage("单位缺失：明细行未填单位，物料档案也没有基本单位，不能试算");
            return out;
        }
        // 取整策略的唯一权威：roundStock 不认识该单位即该行不可试算（不擅自扩展计算器）。
        try {
            BomConsumptionCalculator.roundStock(ExactQuantity.ZERO, rawUnit);
        } catch (BusinessException e) {
            out.setMessage(e.getMessage());
            return out;
        }

        // 辅助能力与原始比例是否已填无关，新选材料可先用跳距生成定额。
        out.setPitchSupported(pitchSupported(unit, widthMm, outputUnit));
        BigDecimal lossRate = row.getLossRate() == null ? BigDecimal.ZERO : row.getLossRate();
        String lossError = lossError(lossRate);
        if (lossError != null) {
            out.setMessage(lossError);
            return out;
        }
        BigDecimal minIssueQty = row.getMinIssueQty() == null ? BigDecimal.ZERO : row.getMinIssueQty();
        String minIssueError = minIssueError(minIssueQty);
        if (minIssueError != null) {
            out.setMessage(minIssueError);
            return out;
        }

        BigDecimal requestedLength = row.getReferenceRollLengthM();
        if (requestedLength != null && (requestedLength.signum() <= 0 || maxScale(requestedLength) > 4
                || integerDigits(requestedLength) > 8)) {
            out.setMessage("参考卷长须为正数，最多4位小数、8位整数");
            return out;
        }
        BigDecimal baseQty = row.getBaseQty();
        BigDecimal moduleQty = row.getModuleQty();
        String mode;
        try {
            mode = normalizeConsumptionMode(row.getConsumptionMode());
        } catch (BusinessException e) {
            out.setMessage(e.getMessage());
            return out;
        }
        out.setConsumptionMode(mode);
        if (mode != null) {
            try {
                applyByMode(out, row, mode, outputQuantity, unit, widthMm, lengthMm, lossRate, minIssueQty);
            } catch (BusinessException | ArithmeticException e) {
                out.setMessage("试算失败：" + e.getMessage());
                out.setValid(false);
            }
            return out;
        }
        String ratioError = ratioError(baseQty, moduleQty);
        if (ratioError != null) {
            out.setMessage(ratioError);
            return out;
        }
        try {
            // 每件净耗：与领料同一条 theoretical 链路，1 件产出对应的精确比例。
            ExactQuantity unitQuantity = BomConsumptionCalculator.theoretical(BigDecimal.ONE, baseQty, moduleQty, null);
            // 总需求：叠加损耗与最低投料后的精确值；禁止用已舍入的净耗反乘。
            ExactQuantity applied = BomConsumptionCalculator.appliedDemand(
                    outputQuantity, baseQty, moduleQty, null, null, lossRate, minIssueQty);
            BigDecimal total = BomConsumptionCalculator.roundStock(applied, unit);

            out.setUnitConsumption(unitQuantity.toBigDecimal(DISPLAY_SCALE, RoundingMode.HALF_UP).toPlainString());
            out.setTotalConsumption(total.toPlainString());
            // 精确值原样输出（整数也保留 "/1"），与 004 的 exactConsumptionText 口径一致。
            out.setExactConsumption(applied.exactText());

            // 参考米数：以最终取整总量换算，不用未取整中间值；其它连续单位不猜系数。
            ExactQuantity metres = metres(total, unit, widthMm);
            out.setMetres(metres == null ? null : metres.toBigDecimal(DISPLAY_SCALE, RoundingMode.HALF_UP).toPlainString());
            BigDecimal rollLength = referenceRollLength(row.getReferenceRollLengthM(), unit, lengthMm);
            out.setReferenceRollLengthM(rollLength);
            out.setReferenceRollCount(referenceRollCount(metres, rollLength));
            out.setPitchSupported(pitchSupported(unit, widthMm, outputUnit));
            out.setValid(true);
        } catch (BusinessException | ArithmeticException e) {
            out.setMessage("试算失败：" + e.getMessage());
            out.setValid(false);
        }
        return out;
    }

    /** 来源方式：空=历史比例口径；非法值直接拒绝（不猜默认）。 */
    private static String normalizeConsumptionMode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String mode = value.trim().toLowerCase();
        return switch (mode) {
            case "roll_cut", "sheet_nest", "direct", "continuous" -> mode;
            default -> throw new BusinessException("来源方式只能是 roll_cut/sheet_nest/direct/continuous（收到 "
                    + value.trim() + "）");
        };
    }

    /**
     * 按来源方式四分支计算（dev-20261112-008），四分支共用同一入口，禁止一套公式硬套。
     * 结果只用于展示：不写库存、不预留、不生成流水。
     */
    private void applyByMode(BomConsumptionPreviewVO.Row out, BomConsumptionPreviewRequest.Row row, String mode,
            BigDecimal outputQuantity, String unit, BigDecimal widthMm, BigDecimal lengthMm,
            BigDecimal lossRate, BigDecimal minIssueQty) {
        BigDecimal lossFactor = BigDecimal.ONE.add(lossRate.movePointLeft(2));
        BigDecimal total;
        BigDecimal unitQty;
        ExactQuantity metresExact = null;
        switch (mode) {
            case "roll_cut" -> {
                BigDecimal pitch = row.getPitchMm();
                int pcsPerPitch = row.getPcsPerPitch() == null ? 1 : row.getPcsPerPitch();
                if (pitch == null || pitch.signum() <= 0) {
                    throw new BusinessException("卷材裁切请填写跳距(mm)且大于 0");
                }
                if (pcsPerPitch < 1) {
                    throw new BusinessException("每跳产出片数必须大于等于 1");
                }
                if (widthMm == null || widthMm.signum() <= 0) {
                    throw new BusinessException("卷材缺少档案卷宽，不能按跳距试算");
                }
                if (!UNIT_M.equals(unit) && !UNIT_M2.equals(unit)) {
                    throw new BusinessException("卷材裁切行的计算单位须为 M 或 M2");
                }
                BigDecimal jumps = outputQuantity.divide(BigDecimal.valueOf(pcsPerPitch), 0, RoundingMode.CEILING);
                BigDecimal metres = jumps.multiply(pitch).movePointLeft(3);
                BigDecimal area = metres.multiply(widthMm.movePointLeft(3));
                BigDecimal base = UNIT_M.equals(unit) ? metres : area;
                total = base.multiply(lossFactor);
                if (total.compareTo(minIssueQty) < 0) {
                    total = minIssueQty;
                }
                total = total.setScale(DISPLAY_SCALE, RoundingMode.HALF_UP);
                unitQty = total.divide(outputQuantity, DISPLAY_SCALE, RoundingMode.HALF_UP);
                metresExact = ExactQuantity.fromDecimal(metres);
                out.setMetres(metres.setScale(DISPLAY_SCALE, RoundingMode.HALF_UP).toPlainString());
                out.setAreaM2(area.setScale(DISPLAY_SCALE, RoundingMode.HALF_UP).toPlainString());
            }
            case "sheet_nest" -> {
                String ratioError = ratioError(row.getBaseQty(), row.getModuleQty());
                if (ratioError != null) {
                    throw new BusinessException(ratioError);
                }
                BigDecimal sheets = outputQuantity.multiply(row.getBaseQty())
                        .divide(row.getModuleQty(), 0, RoundingMode.CEILING);
                total = sheets.multiply(lossFactor);
                if (total.compareTo(minIssueQty) < 0) {
                    total = minIssueQty;
                }
                total = total.setScale(DISPLAY_SCALE, RoundingMode.HALF_UP);
                unitQty = total.divide(outputQuantity, DISPLAY_SCALE, RoundingMode.HALF_UP);
            }
            case "direct" -> {
                total = outputQuantity.multiply(lossFactor);
                if (total.compareTo(minIssueQty) < 0) {
                    total = minIssueQty;
                }
                total = total.setScale(DISPLAY_SCALE, RoundingMode.HALF_UP);
                unitQty = total.divide(outputQuantity, DISPLAY_SCALE, RoundingMode.HALF_UP);
            }
            case "continuous" -> {
                BigDecimal perSquareMetre = row.getUnitConsumption();
                if (perSquareMetre == null || perSquareMetre.signum() <= 0) {
                    throw new BusinessException("连续耗材请填写单位面积单耗且大于 0");
                }
                if (widthMm == null || widthMm.signum() <= 0 || lengthMm == null || lengthMm.signum() <= 0) {
                    throw new BusinessException("连续耗材缺少档案宽/长，无法计算面积");
                }
                BigDecimal areaPerPiece = widthMm.multiply(lengthMm).movePointLeft(6);
                BigDecimal area = areaPerPiece.multiply(outputQuantity);
                total = area.multiply(perSquareMetre).multiply(lossFactor).setScale(DISPLAY_SCALE, RoundingMode.HALF_UP);
                if (total.compareTo(minIssueQty) < 0) {
                    total = minIssueQty.setScale(DISPLAY_SCALE, RoundingMode.HALF_UP);
                }
                unitQty = areaPerPiece.multiply(perSquareMetre).setScale(DISPLAY_SCALE, RoundingMode.HALF_UP);
                out.setAreaM2(area.setScale(DISPLAY_SCALE, RoundingMode.HALF_UP).toPlainString());
            }
            default -> throw new BusinessException("未知来源方式：" + mode);
        }
        out.setUnitConsumption(unitQty.toPlainString());
        out.setTotalConsumption(total.toPlainString());
        out.setExactConsumption(total.stripTrailingZeros().toPlainString());
        BigDecimal rollLength = referenceRollLength(row.getReferenceRollLengthM(), unit, lengthMm);
        out.setReferenceRollLengthM(rollLength);
        out.setReferenceRollCount(referenceRollCount(metresExact, rollLength));
        out.setValid(true);
    }

    /** baseQty / moduleQty 必须同时存在、>0、decimal(12,4) 可表达；缺比例不回退历史用量。 */
    private static String ratioError(BigDecimal baseQty, BigDecimal moduleQty) {
        if (baseQty == null || moduleQty == null) {
            return "请填写模数和基数，不能在缺少原始比例时推定耗用";
        }
        String baseError = bomQuantityError(baseQty, "基数");
        if (baseError != null) {
            return baseError;
        }
        return bomQuantityError(moduleQty, "模数");
    }

    /** 单个 BOM 比例字段：>0、最多 4 位小数、整数位不超 decimal(12,4)。 */
    private static String bomQuantityError(BigDecimal value, String field) {
        if (value.signum() <= 0) {
            return field + " 必须大于 0（收到 " + value.toEngineeringString() + "）";
        }
        if (maxScale(value) > BOM_QUANTITY_SCALE) {
            return field + " 最多支持 " + BOM_QUANTITY_SCALE + " 位小数（收到 " + value.toEngineeringString() + "）";
        }
        if (integerDigits(value) > BOM_QUANTITY_INTEGER_DIGITS) {
            return field + " 整数位最多8位（收到 " + value.toEngineeringString() + "）";
        }
        return null;
    }

    /** 损耗率：0..100，最多 2 位小数。 */
    private static String lossError(BigDecimal lossRate) {
        if (lossRate.signum() < 0 || lossRate.compareTo(HUNDRED) > 0) {
            return "损耗率 须在 0 到 100 之间（收到 " + lossRate.toEngineeringString() + "）";
        }
        if (maxScale(lossRate) > LOSS_MAX_SCALE) {
            return "损耗率 最多支持 " + LOSS_MAX_SCALE + " 位小数（收到 " + lossRate.toEngineeringString() + "）";
        }
        return null;
    }

    /** 最低投料：≥0、最多 4 位小数、整数位不超 decimal(12,4)。 */
    private static String minIssueError(BigDecimal minIssueQty) {
        if (minIssueQty.signum() < 0) {
            return "最低投料 不能为负（收到 " + minIssueQty.toEngineeringString() + "）";
        }
        if (maxScale(minIssueQty) > BOM_QUANTITY_SCALE) {
            return "最低投料 最多支持 " + BOM_QUANTITY_SCALE + " 位小数（收到 "
                    + minIssueQty.toEngineeringString() + "）";
        }
        if (integerDigits(minIssueQty) > BOM_QUANTITY_INTEGER_DIGITS) {
            return "最低投料 整数位最多8位（收到 " + minIssueQty.toEngineeringString() + "）";
        }
        return null;
    }

    /** 参考米数：M 即取整总量；M2 需档案宽>0，否则不猜系数返回 null。 */
    private static ExactQuantity metres(BigDecimal total, String unit, BigDecimal widthMm) {
        if (UNIT_M.equals(unit)) {
            return ExactQuantity.fromDecimal(total);
        }
        if (UNIT_M2.equals(unit)) {
            if (widthMm == null || widthMm.signum() <= 0) {
                return null;
            }
            BigDecimal widthMetres = widthMm.movePointLeft(3);
            return ExactQuantity.fromDecimal(total)
                    .divide(ExactQuantity.fromDecimal(widthMetres));
        }
        return null;
    }

    /** 参考卷长：请求显式 >0 优先；否则仅连续计量 M/M2 用档案 lengthMm/1000 作名义参考。 */
    private static BigDecimal referenceRollLength(BigDecimal requested, String unit, BigDecimal lengthMm) {
        if (requested != null && requested.signum() > 0) {
            return requested;
        }
        if ((UNIT_M.equals(unit) || UNIT_M2.equals(unit)) && lengthMm != null && lengthMm.signum() > 0) {
            return lengthMm.movePointLeft(3);
        }
        return null;
    }

    /** 参考卷数 = ceil(米数 / 参考卷长)，整数字符串；任一项缺失返回 null。 */
    private static String referenceRollCount(ExactQuantity metres, BigDecimal rollLength) {
        if (metres == null || rollLength == null || rollLength.signum() <= 0) {
            return null;
        }
        BigInteger count = metres
                .divide(ExactQuantity.fromDecimal(rollLength))
                .ceilToInteger();
        return count.toString();
    }

    /**
     * 仅 M/M2、档案宽>0、产出单位严格为 PCS 时标记可借助 004 跳距接口。
     *
     * <p>不把 SET / EA / 卷等其它件数或卷类单位视为“每跳 PCS”（主会话勘误，2026-11-12）：
     * 跳距是卷材按张/片送料的口径，多件套产出与整卷产出都不适用。本接口不计算每跳 PCS。</p>
     */
    private static boolean pitchSupported(String unit, BigDecimal widthMm, String outputUnit) {
        if (!UNIT_M.equals(unit) && !UNIT_M2.equals(unit)) {
            return false;
        }
        if (widthMm == null || widthMm.signum() <= 0) {
            return false;
        }
        return DEFAULT_OUTPUT_UNIT.equals(BomConsumptionCalculator.normalizeUnit(outputUnit));
    }

    /** 去空白；空白串按缺失处理。 */
    private static String normalizeText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    /** 有效小数位：stripTrailingZeros 后判断，195.00 视为 0 位。 */
    private static int maxScale(BigDecimal value) {
        return Math.max(0, value.stripTrailingZeros().scale());
    }

    /** 整数位数：按 stripTrailingZeros 后的 precision−scale 计算，不展开 1E+100 这类指数形式。 */
    private static long integerDigits(BigDecimal value) {
        BigDecimal stripped = value.stripTrailingZeros();
        return (long) stripped.precision() - stripped.scale();
    }
}
