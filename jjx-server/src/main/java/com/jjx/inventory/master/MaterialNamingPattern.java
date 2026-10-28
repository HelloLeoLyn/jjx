package com.jjx.inventory.master;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.inventory.domain.InventoryMaterial;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 命名规则：按品类配置「勾选哪些字段 + 顺序 + 分隔符」。dev-20261028-005
 *
 * 可用字段 = 公共字段（brand/model/color/meta/form/unit）+ 本品类属性模板里的字段编码；
 * 取值优先用物料主数据列，属性字段取 attribute_json（枚举按选项名称输出）。
 */
public record MaterialNamingPattern(List<String> fields, String separator, String empty) {

    public static final List<String> COMMON_TOKENS =
            List.of("brand", "model", "color", "meta", "form", "unit", "size");
    public static final String DEFAULT_SEPARATOR = " ";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static MaterialNamingPattern defaults() {
        return new MaterialNamingPattern(List.of("brand", "model", "thickness_mm", "width", "length"), DEFAULT_SEPARATOR, "skip");
    }

    /** 归一：去重保序、字段名小写；分隔符取首个字符，缺省空格；empty 只接受 skip/placeholder。 */
    public MaterialNamingPattern normalized() {
        Set<String> keys = new LinkedHashSet<>();
        for (String field : fields == null ? List.<String>of() : fields) {
            if (field != null && !field.isBlank()) keys.add(field.trim().toLowerCase());
        }
        String sep = separator == null || separator.isEmpty() ? DEFAULT_SEPARATOR : separator.substring(0, 1);
        String emptyMode = "placeholder".equals(empty) ? "placeholder" : "skip";
        return new MaterialNamingPattern(List.copyOf(keys), sep, emptyMode);
    }

    /** 允许的字段：公共字段 + 本品类属性字段。 */
    public void validate(MaterialAttributeSchema schema) {
        Set<String> allowed = new LinkedHashSet<>(COMMON_TOKENS);
        if (schema != null) schema.fields().forEach(field -> allowed.add(field.key()));
        for (String field : normalized().fields()) {
            if (!allowed.contains(field)) {
                throw new com.jjx.common.exception.BusinessException("命名规则含未知字段：" + field);
            }
        }
    }

    /** 生成名称；缺值按 skip（跳过）或 placeholder（占位 —）处理。 */
    public String compose(InventoryMaterial material, MaterialAttributeSchema schema) {
        MaterialNamingPattern pattern = normalized();
        if (pattern.fields().isEmpty()) return null;
        Map<String, Object> attributes = readAttributes(material.getAttributeJson());
        MaterialSizeValue.SizeSpec spec = MaterialSizeValue.spec(material.getSize());
        List<String> parts = new ArrayList<>();
        for (String field : pattern.fields()) {
            String value = valueOf(material, schema, attributes, spec, field);
            if (value == null || value.isBlank()) {
                if ("placeholder".equals(pattern.empty())) parts.add("—");
                continue;
            }
            parts.add(value.trim());
        }
        return parts.isEmpty() ? null : String.join(pattern.separator(), parts);
    }

    private static String valueOf(InventoryMaterial material, MaterialAttributeSchema schema,
                                  Map<String, Object> attributes, MaterialSizeValue.SizeSpec spec, String field) {
        return switch (field) {
            case "brand" -> material.getBrand();
            case "model" -> material.getModel();
            case "color" -> colorText(material.getColor());
            case "meta" -> material.getMaterialMeta();
            case "form" -> material.getMaterialForm();
            case "unit" -> material.getUnit();
            case "size" -> sizeText(material, spec);
            case "width" -> decimalText(firstNonNull(material.getWidthMm(), spec == null ? null : spec.widthMm()));
            case "length" -> decimalText(firstNonNull(material.getLengthMm(), spec == null ? null : spec.lengthMm()));
            case "thickness_mm" -> decimalText(firstNonNull(material.getThicknessMm(), spec == null ? null : spec.thicknessMm()));
            case "grammage" -> decimalText(material.getGrammage());
            default -> optionText(schema, attributes, field);
        };
    }

    private static java.math.BigDecimal firstNonNull(java.math.BigDecimal primary, java.math.BigDecimal fallback) {
        return primary != null ? primary : fallback;
    }

    private static String decimalText(java.math.BigDecimal value) {
        if (value == null) return null;
        return value.stripTrailingZeros().toPlainString();
    }

    /** 结构化尺寸：宽×长×厚（有则带），统一 × 与 mm；无结构值时原样返回 size 文本。 */
    private static String sizeText(InventoryMaterial material, MaterialSizeValue.SizeSpec spec) {
        java.math.BigDecimal width = firstNonNull(material.getWidthMm(), spec == null ? null : spec.widthMm());
        java.math.BigDecimal length = firstNonNull(material.getLengthMm(), spec == null ? null : spec.lengthMm());
        java.math.BigDecimal thickness = firstNonNull(material.getThicknessMm(), spec == null ? null : spec.thicknessMm());
        if (width == null && length == null && thickness == null) return material.getSize();
        List<String> parts = new ArrayList<>();
        if (width != null) parts.add(decimalText(width));
        if (length != null) parts.add(decimalText(length));
        if (thickness != null) parts.add(decimalText(thickness));
        return String.join("×", parts) + (material.getSizeUnit() == null ? "mm" : material.getSizeUnit());
    }

    /** 颜色存代码，命名用名称；映射缺失时回退代码。 */
    private static String colorText(String code) {
        if (code == null || code.isBlank()) return null;
        return switch (code) {
            case "BLACK" -> "黑";
            case "WHITE" -> "白";
            case "YELLOW" -> "黄";
            case "RED" -> "红";
            case "BLUE" -> "蓝";
            case "GRAY" -> "灰";
            case "SILVER" -> "银";
            case "TRANSPARENT" -> "透明";
            default -> code;
        };
    }

    /** 属性值转文字：枚举/多枚举输出选项名称，布尔输出是/否文字，其余原样。 */
    private static String optionText(MaterialAttributeSchema schema, Map<String, Object> attributes, String field) {
        Object value = attributes.get(field);
        if (value == null) return null;
        MaterialAttributeSchema.Field definition = schema == null ? null : schema.fields().stream()
                .filter(item -> item.key().equals(field)).findFirst().orElse(null);
        if (definition != null && ("enum".equals(definition.type()) || "multienum".equals(definition.type()))) {
            List<String> codes = value instanceof List<?> list
                    ? list.stream().map(String::valueOf).toList()
                    : List.of(String.valueOf(value));
            List<String> labels = new ArrayList<>();
            for (String code : codes) {
                String label = definition.options() == null ? null : definition.options().stream()
                        .filter(option -> option.code().equals(code)).map(MaterialAttributeSchema.Option::label)
                        .findFirst().orElse(code);
                labels.add(label);
            }
            return String.join("、", labels);
        }
        if (definition != null && "boolean".equals(definition.type())) {
            boolean truthy = Boolean.parseBoolean(String.valueOf(value));
            String label = truthy ? definition.trueLabel() : definition.falseLabel();
            return label == null || label.isBlank() ? null : label;
        }
        return String.valueOf(value);
    }

    private static Map<String, Object> readAttributes(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return MAPPER.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { });
        } catch (Exception e) {
            return Map.of();
        }
    }
}
