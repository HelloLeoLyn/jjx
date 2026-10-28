package com.jjx.inventory.util;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Pattern;

/** 物料尺寸唯一解析入口。每段单位独立，内部统一为毫米，原始输入由调用方保留。 */
public final class MaterialSizeParser {
    private MaterialSizeParser() {}

    private static final String NUMBER = "(?:[0-9]+(?:\\.[0-9]+)?|\\.[0-9]+)";
    private static final String UNIT = "(?:mm|cm|inch|in|m|毫米|厘米|米|英寸|\")?";
    private static final String PART = NUMBER + "\\s*" + UNIT;
    private static final Pattern DIMENSION = Pattern.compile("^(" + NUMBER + ")\\s*(" + UNIT + ")$", Pattern.CASE_INSENSITIVE);
    private static final Pattern LEGACY = Pattern.compile("(?<![A-Za-z\\d.])" + PART + "(?:\\s*[×xX*＊]\\s*" + PART + "){1,2}", Pattern.CASE_INSENSITIVE);
    public record Dimensions(BigDecimal widthMm, BigDecimal lengthMm, BigDecimal thicknessMm) { }

    /** 两段按宽×长、三段按宽×长×厚；不再按品类猜卷料（旧品类码已废弃）。 */
    public static Dimensions parse(String size) {
        if (size == null || size.isBlank()) return new Dimensions(null, null, null);
        if (size.length() > 50) throw new IllegalArgumentException("尺寸不能超过50个字符");
        String[] parts = size.trim().split("[×xX*＊]", -1);
        if (parts.length > 3) throw new IllegalArgumentException("尺寸最多三维：宽×长/厚×厚");
        BigDecimal[] values = new BigDecimal[parts.length];
        for (int i = 0; i < parts.length; i++) {
            var match = DIMENSION.matcher(parts[i].trim());
            if (!match.matches()) throw new IllegalArgumentException("尺寸格式错误：" + parts[i]);
            BigDecimal factor = switch (match.group(2).toLowerCase(Locale.ROOT)) {
                case "cm", "厘米" -> BigDecimal.TEN;
                case "m", "米" -> new BigDecimal("1000");
                case "in", "inch", "英寸", "\"" -> new BigDecimal("25.4");
                default -> BigDecimal.ONE;
            };
            values[i] = new BigDecimal(match.group(1)).multiply(factor);
            if (values[i].signum() <= 0) throw new IllegalArgumentException("尺寸必须大于0");
        }
        if (parts.length == 1) return new Dimensions(values[0], null, null);
        return new Dimensions(values[0], values[1], parts.length == 3 ? values[2] : null);
    }

    /** 旧规格可能混有工艺文字；只提取尺寸片段，不更改规格。 */
    public static String extractLegacy(String specification) {
        if (specification == null) return null;
        var match = LEGACY.matcher(specification);
        if (match.find()) return match.group().trim();
        String single = specification.trim();
        return DIMENSION.matcher(single).matches() ? single : null;
    }
}
