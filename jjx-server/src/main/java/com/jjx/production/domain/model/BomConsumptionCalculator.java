package com.jjx.production.domain.model;

import com.jjx.common.exception.BusinessException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Set;

/** BOM原始比例是权威，保存快照和最终库存精度分别处理（dev-20261111-016）。 */
public final class BomConsumptionCalculator {
    private static final Set<String> PIECES = Set.of("PCS", "PC", "EA", "SET", "PIN", "SHEET",
            "个", "件", "片", "张", "只", "套", "支", "条", "瓶", "卷", "ROLL", "ROLLS", "COIL");
    private static final Set<String> CONTINUOUS = Set.of("M", "M2", "KG", "G", "L", "ML");
    private BomConsumptionCalculator() { }

    public static ExactQuantity theoretical(BigDecimal output, BigDecimal base, BigDecimal module,
                                             BigDecimal legacyUnitQuantity) {
        if (output == null || output.signum() < 0) throw new BusinessException("生产数量不能为负或为空");
        ExactQuantity unit;
        if (base != null && module != null && base.signum() > 0 && module.signum() > 0) {
            unit = ExactQuantity.fromDecimal(base).divide(ExactQuantity.fromDecimal(module));
        } else {
            if (legacyUnitQuantity == null || legacyUnitQuantity.signum() < 0)
                throw new BusinessException("BOM原始比例及历史用量缺失，不能推定领料需求");
            unit = ExactQuantity.fromDecimal(legacyUnitQuantity);
        }
        return ExactQuantity.fromDecimal(output).multiply(unit);
    }

    public static ExactQuantity lossApplied(ExactQuantity theoretical, BigDecimal lossPercent) {
        BigDecimal loss = lossPercent == null ? BigDecimal.ZERO : lossPercent;
        if (loss.signum() < 0 || loss.compareTo(new BigDecimal("100")) > 0)
            throw new BusinessException("损耗率须在0到100之间");
        if (loss.stripTrailingZeros().scale() > 2)
            throw new BusinessException("损耗率最多2位小数，不能静默舍入");
        return theoretical.multiply(ExactQuantity.ONE.add(ExactQuantity.fromDecimal(loss).divide(ExactQuantity.of(100))));
    }

    public static ExactQuantity appliedDemand(BigDecimal output, BigDecimal base, BigDecimal module,
            BigDecimal legacyUnit, BigDecimal legacyApplied, BigDecimal loss, BigDecimal minimum) {
        output = output == null ? BigDecimal.ZERO : output;
        ExactQuantity applied;
        if ((base == null || module == null) && legacyUnit == null && legacyApplied != null) {
            applied = ExactQuantity.fromDecimal(legacyApplied).multiply(ExactQuantity.fromDecimal(output));
        } else {
            applied = lossApplied(theoretical(output, base, module, legacyUnit), loss);
        }
        ExactQuantity floor = ExactQuantity.fromDecimal(minimum == null ? BigDecimal.ZERO : minimum);
        return applied.compareTo(floor) >= 0 ? applied : floor;
    }

    public record Conversion(ExactQuantity factor, String basis, String source) { }

    /** 需求试算与领料共用；不解析规格文本。 */
    public static Conversion conversion(String fromUnit, String toUnit, BigDecimal factor,
            String basis, String source, BigDecimal width, BigDecimal length, String path) {
        String from = normalizeUnit(fromUnit), to = normalizeUnit(toUnit);
        if (from.isBlank() || to.isBlank()) throw new BusinessException(path + " 单位缺失，不能推定换算");
        if (from.equals(to)) return new Conversion(ExactQuantity.ONE, "同单位", "同一单位");
        if (factor != null) {
            if (factor.signum() <= 0) throw new BusinessException(path + " 换算系数必须大于0");
            if (basis == null || basis.isBlank()) throw new BusinessException(path + " 换算系数缺少可核实依据");
            return new Conversion(ExactQuantity.fromDecimal(factor), basis, source);
        }
        if (Set.of("卷", "ROLL", "ROLLS", "COIL").contains(from)
                || Set.of("卷", "ROLL", "ROLLS", "COIL").contains(to))
            throw new BusinessException(path + " 卷类计量不能用档案名义长度换算，逐卷余量属013（UNIT_CONVERSION_MISSING）");
        if (PIECES.contains(from) && "M2".equals(to) && width != null && width.signum() > 0
                && length != null && length.signum() > 0)
            return new Conversion(ExactQuantity.fromDecimal(width).multiply(ExactQuantity.fromDecimal(length))
                    .divide(ExactQuantity.of(1000000)), "档案尺寸" + width + "×" + length + "mm",
                    "inventory_material.width_mm/length_mm");
        throw new BusinessException(path + " 单位[" + fromUnit + "→" + toUnit
                + "]缺少已保存换算系数/可核实依据（UNIT_CONVERSION_MISSING）；批次实际长度属013");
    }

    public static BigDecimal roundStock(ExactQuantity quantity, String unit) {
        String normalized = normalizeUnit(unit);
        if (PIECES.contains(normalized)) return new BigDecimal(quantity.ceilToInteger());
        if (CONTINUOUS.contains(normalized)) return quantity.toBigDecimal(4, RoundingMode.HALF_UP);
        throw new BusinessException("单位[" + unit + "]未登记取整策略（UNIT_POLICY_MISSING）");
    }

    public static String normalizeUnit(String unit) {
        if (unit == null) return "";
        return switch (unit.trim().toUpperCase(Locale.ROOT)) {
            case "㎡", "平方米", "平方" -> "M2";
            case "米" -> "M";
            case "公斤" -> "KG";
            default -> unit.trim().toUpperCase(Locale.ROOT);
        };
    }
}
