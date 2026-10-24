package com.jjx.inventory.master;

import com.jjx.common.exception.BusinessException;
import java.math.BigDecimal;
import java.util.Arrays;

/** size 为尺寸事实；旧厚度列仅作查询兼容，不接受独立录入。 */
public final class MaterialSizeValue {
    private MaterialSizeValue() { }

    public static String[] parts(String size) {
        if (size == null || size.isBlank()) return new String[0];
        String body = size.trim().replaceFirst("(?i)mm$", "").trim();
        boolean round = body.matches("^[ΦφØø⌀].*");
        if (round) body = body.substring(1).trim();
        String[] parts = body.split("[×xX*]", -1);
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

    public static String thickness(String size) {
        String[] parts = parts(size);
        if (parts == null || parts.length == 0) return null;
        int index = size.trim().matches("^[ΦφØø⌀].*") ? 1 : 2;
        return parts.length > index && !parts[index].isEmpty() ? parts[index] : null;
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
