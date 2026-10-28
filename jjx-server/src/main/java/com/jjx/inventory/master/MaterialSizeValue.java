package com.jjx.inventory.master;

import com.jjx.common.exception.BusinessException;
import java.math.BigDecimal;
import java.util.Arrays;

/** 数值尺寸校验与只读渲染；文本解析仅供历史导入使用。 */
public final class MaterialSizeValue {
    private MaterialSizeValue() { }

    public static SizeSpec validate(BigDecimal width, BigDecimal length, BigDecimal thickness, String unit) {
        positive(width, 2, 8, "宽");
        positive(length, 2, 10, "长");
        positive(thickness, 4, 6, "厚");
        String normalizedUnit = unit == null || unit.isBlank() ? "mm" : unit.trim();
        if (!java.util.Set.of("mm", "cm", "m").contains(normalizedUnit)) throw new BusinessException("尺寸单位只允许 mm/cm/m");
        return new SizeSpec(width, length, thickness, normalizedUnit);
    }

    private static void positive(BigDecimal value, int scale, int integers, String label) {
        if (value != null && (value.signum() <= 0 || value.stripTrailingZeros().scale() > scale
                || value.precision() - value.scale() > integers)) {
            throw new BusinessException(label + "须为大于0的数值，最多" + scale + "位小数");
        }
    }

    /** 缺项保留位置，避免将宽*厚误读成宽*长；单位仅在有尺寸时展示。 */
    public static String render(BigDecimal width, BigDecimal length, BigDecimal thickness, String unit) {
        if (width == null && length == null && thickness == null) return null;
        String[] values = {number(width), number(length), number(thickness)};
        int count = thickness != null ? 3 : length != null ? 2 : 1;
        return String.join("*", Arrays.copyOf(values, count)) + (unit == null ? "mm" : unit);
    }

    private static String number(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    /** 尺寸分隔符：历史 ×、字母 x/X 与全角 ＊ 都按同一分隔符识别，输出统一为 *。 */
    private static final String SEPARATORS = "[×xX*＊]";

    public static String[] parts(String size) {
        if (size == null || size.isBlank()) return new String[0];
        String body = size.trim().replaceFirst("(?i)mm$", "").trim();
        boolean round = body.matches("^[ΦφØø⌀].*");
        if (round) body = body.substring(1).trim();
        String[] parts = body.split(SEPARATORS, -1);
        if (parts.length > (round ? 2 : 3) || Arrays.stream(parts).allMatch(String::isBlank)) return null;
        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].trim();
            if (!parts[i].isEmpty() && (!parts[i].matches("\\d+(?:\\.\\d+)?") || new BigDecimal(parts[i]).signum() <= 0)) return null;
        }
        return parts;
    }

    public static void validate(String size) {
        if (size != null && size.length() > 50) throw new BusinessException("尺寸不能超过50字");
        if (parts(size) == null) throw new BusinessException("尺寸请填宽*长*厚或Φ直径*厚，数值须大于0，可带mm；缺项保留位置");
    }

    /** 可识别尺寸统一为半角 *；Φ、缺项占位与单位原样保留，无法识别的原文不改写。 */
    public static String normalize(String size) {
        if (size == null || size.isBlank()) return size;
        String text = size.trim();
        if (parts(text) == null) return text;
        String unit = text.matches("(?i).*mm$") ? text.substring(text.length() - 2) : "";
        String body = (unit.isEmpty() ? text : text.substring(0, text.length() - 2)).trim();
        boolean round = body.matches("^[ΦφØø⌀].*");
        if (round) body = body.substring(1).trim();
        String[] values = body.split(SEPARATORS, -1);
        for (int i = 0; i < values.length; i++) values[i] = values[i].trim();
        return (round ? "Φ" : "") + String.join("*", values) + unit;
    }

    public static String thickness(String size) {
        String[] parts = parts(size);
        if (parts == null || parts.length == 0) return null;
        int index = size.trim().matches("^[ΦφØø⌀].*") ? 1 : 2;
        return parts.length > index && !parts[index].isEmpty() ? parts[index] : null;
    }

    /** 结构化尺寸（宽/长/厚，mm）；与 size 文本同源，识别不了返回 null。 */
    public record SizeSpec(BigDecimal widthMm, BigDecimal lengthMm, BigDecimal thicknessMm, String unit) { }

    public static SizeSpec spec(String size) {
        String[] parts = parts(size);
        if (parts == null || parts.length == 0) return null;
        boolean round = size != null && size.trim().matches("^[ΦφØø⌀].*");
        BigDecimal first = decimal(parts[0]);
        BigDecimal second = parts.length > 1 ? decimal(parts[1]) : null;
        BigDecimal third = parts.length > 2 ? decimal(parts[2]) : null;
        // 圆形料：Φ直径*厚；方形料：宽*长(*厚)。
        return round
                ? new SizeSpec(first, null, second, "mm")
                : new SizeSpec(first, second, third, "mm");
    }

    private static BigDecimal decimal(String value) {
        return value == null || value.isBlank() ? null : new BigDecimal(value);
    }

    public static String mergeLegacy(String size, Object thickness) {
        if (thickness == null || thickness.toString().isBlank() || thickness(size) != null) return size;
        String[] parts = parts(size);
        if (parts == null) return size;
        String value = thickness.toString().trim().replaceFirst("(?i)mm$", "").trim();
        if (!value.matches("\\d+(?:\\.\\d+)?") || new BigDecimal(value).signum() <= 0) return size;
        boolean round = size != null && size.trim().matches("^[ΦφØø⌀].*");
        String[] merged = new String[round ? 2 : 3];
        Arrays.fill(merged, "");
        System.arraycopy(parts, 0, merged, 0, Math.min(parts.length, merged.length));
        merged[merged.length - 1] = value;
        String unit = size == null || size.isBlank() ? "mm" : size.trim().matches("(?i).*mm$") ? size.trim().substring(size.trim().length() - 2) : "";
        return (round ? "Φ" : "") + String.join("*", merged) + unit;
    }
}
