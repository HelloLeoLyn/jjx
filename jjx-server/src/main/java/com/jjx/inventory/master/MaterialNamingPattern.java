package com.jjx.inventory.master;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.inventory.domain.InventoryMaterial;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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
public record MaterialNamingPattern(List<String> fields, String separator, String empty,
                                    /** 每个字段自己的分隔符（1 个字符，空=紧贴上一个）；缺省回落到 separator（dev-20261029-024）。 */
                                    Map<String, String> separators,
                                    /** 尺寸字段的输出单位；不配置保持原值，不输出单位文字。 */
                                    Map<String, String> units) {

    public MaterialNamingPattern(List<String> fields, String separator, String empty, Map<String, String> separators) {
        this(fields, separator, empty, separators, Map.of());
    }

    /** 兼容旧规则：只有全局分隔符。 */
    public MaterialNamingPattern(List<String> fields, String separator, String empty) {
        this(fields, separator, empty, Map.of());
    }

    /** 命名/规格共用的可选项真源（新增字段只改这里，前端自动出现）。 */
    public static final java.util.LinkedHashMap<String, String> COMMON_LABELS = new java.util.LinkedHashMap<>();
    static {
        COMMON_LABELS.put("category", "品类");
        COMMON_LABELS.put("categoryprefix", "品类前缀");
        COMMON_LABELS.put("brand", "品牌");
        COMMON_LABELS.put("brandabbr", "品牌·缩写");
        COMMON_LABELS.put("meta", "材质");
        COMMON_LABELS.put("form", "形态");
        COMMON_LABELS.put("unit", "单位");
        COMMON_LABELS.put("model", "型号");
        COMMON_LABELS.put("color", "颜色");
        COMMON_LABELS.put("width", "宽");
        COMMON_LABELS.put("length", "长");
        COMMON_LABELS.put("thickness_mm", "厚度");
        COMMON_LABELS.put("grammage", "克重");
        COMMON_LABELS.put("supplier", "供应商");
        COMMON_LABELS.put("suppliershort", "供应商·简称");
        COMMON_LABELS.put("supplierabbr", "供应商·缩写");
        COMMON_LABELS.put("customersupplied", "客供");
        COMMON_LABELS.put("sampleonly", "样品");
    }
    public static final List<String> COMMON_TOKENS = List.copyOf(COMMON_LABELS.keySet());
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
        Map<String, String> perField = new LinkedHashMap<>();
        if (separators != null) {
            for (String key : keys) {
                String value = separators.get(key);
                if (value == null) continue;
                // 分隔符只能 1 个字符；空串表示紧贴上一个字段
                perField.put(key, value.isEmpty() ? "" : value.substring(0, 1));
            }
        }
        return new MaterialNamingPattern(List.copyOf(keys), sep, emptyMode, Map.copyOf(perField),
                units == null ? Map.of() : Map.copyOf(units));
    }

    /** 允许的字段：公共字段 + 本品类属性字段。 */
    public void validate(MaterialAttributeSchema schema) {
        Set<String> allowed = new LinkedHashSet<>(COMMON_TOKENS);
        if (schema != null) schema.fields().forEach(field -> allowed.add(field.key()));
        if (units != null) units.forEach((key, unit) -> {
            if (!MaterialAttributeSchema.DIMENSION_KEYS.contains(key)
                    || !MaterialAttributeSchema.DIMENSION_UNITS.contains(unit))
                throw new com.jjx.common.exception.BusinessException("规则尺寸单位只允许宽、长、厚使用 mm/cm/m/μm");
        });
        for (String field : normalized().fields()) {
            if (!allowed.contains(field)) {
                throw new com.jjx.common.exception.BusinessException("命名规则含未知字段：" + field);
            }
        }
    }

    /** 型号一般字段缺值跳过，品类前缀缺失报错；空全局分隔符转为逐字段紧贴，保留旧命名规则的归一行为。 */
    public MaterialNamingPattern forModel() {
        Map<String, String> perField = new LinkedHashMap<>();
        if ("".equals(separator)) normalized().fields().forEach(key -> perField.put(key, ""));
        if (separators != null) perField.putAll(separators);
        return new MaterialNamingPattern(fields, separator, "skip", perField, units);
    }

    /** 型号与命名共用字段白名单，型号不允许引用自身。 */
    public void validateModel(MaterialAttributeSchema schema) {
        if (normalized().fields().contains("model"))
            throw new com.jjx.common.exception.BusinessException("型号规则不能包含型号自身（model）");
        validate(schema);
    }

    /** 生成名称；缺值按 skip（跳过）或 placeholder（占位 —）处理。 */
    public String compose(InventoryMaterial material, MaterialAttributeSchema schema) {
        return compose(material, schema, null);
    }

    /** 生成名称（可带品类名，供 category 字段使用，dev-20261030-005）。 */
    public String compose(InventoryMaterial material, MaterialAttributeSchema schema, String categoryName) {
        return compose(material, schema, categoryName, null);
    }

    /**
     * 生成名称（brandLabel 为品牌的字典显示名，dev-20261105-004）。
     * 品牌列存的是字典代码（BOMING/DIBO），命名要显示中文（博明/地博），故由调用方传入 label；
     * 传 null 时回落到原值，保持单元测试与旧调用不变。
     */
    public String compose(InventoryMaterial material, MaterialAttributeSchema schema, String categoryName, String brandLabel) {
        return compose(material, schema, categoryName, brandLabel, null);
    }

    /** 品牌、颜色统一使用调用方提供的字典名称，缺失时回退原始代码。 */
    public String compose(InventoryMaterial material, MaterialAttributeSchema schema, String categoryName,
                          String brandLabel, String colorLabel) {
        return compose(material, schema, categoryName, brandLabel, colorLabel, null);
    }

    /** 调用方传入品类前缀，缺失时明确报错，预览与新建共用。 */
    public String compose(InventoryMaterial material, MaterialAttributeSchema schema, String categoryName,
                          String brandLabel, String colorLabel, String categoryPrefix) {
        return compose(material, schema, categoryName, brandLabel, colorLabel, categoryPrefix, null);
    }

    /**
     * 生成名称；brandAbbr 为品牌缩写（字典项 ext_data.abbr，dev-20261107-014）。
     * 只有命名规则勾选了「品牌·缩写」才会用到；未配置缩写时回落到品牌中文名。
     */
    public String compose(InventoryMaterial material, MaterialAttributeSchema schema, String categoryName,
                          String brandLabel, String colorLabel, String categoryPrefix, String brandAbbr) {
        return compose(material, schema, categoryName, brandLabel, colorLabel, categoryPrefix, brandAbbr, null, null);
    }

    /**
     * 生成名称；brandAbbr 为品牌缩写，supplierShort/supplierAbbr 为供应商简称与缩写（dev-20261107-017）。
     * 供应商取值回落顺序：缩写 → 简称 → 全名，保证名称里不会出现空段或流水编码。
     */
    public String compose(InventoryMaterial material, MaterialAttributeSchema schema, String categoryName,
                          String brandLabel, String colorLabel, String categoryPrefix, String brandAbbr,
                          String supplierShort, String supplierAbbr) {
        MaterialNamingPattern pattern = normalized();
        if (pattern.fields().isEmpty()) return null;
        Map<String, Object> attributes = readAttributes(material.getAttributeJson());
        MaterialSizeValue.SizeSpec spec = MaterialSizeValue.spec(material.getSize());
        StringBuilder text = new StringBuilder();
        boolean first = true;
        for (String field : pattern.fields()) {
            String value = "categoryprefix".equals(field) ? requireCategoryPrefix(categoryPrefix)
                    : valueOf(material, schema, attributes, spec, field, categoryName, brandLabel, colorLabel, brandAbbr,
                              supplierShort, supplierAbbr);
            if (value != null && !value.isBlank() && pattern.units().containsKey(field)) {
                value = MaterialSizeValue.segment(new java.math.BigDecimal(value), pattern.units().get(field), null).value();
            }
            String part = value == null || value.isBlank()
                    ? ("placeholder".equals(pattern.empty()) ? "—" : null)
                    : value.trim();
            if (part == null) continue;
            if (!first) text.append(pattern.separators().getOrDefault(field, pattern.separator()));
            text.append(part);
            first = false;
        }
        return text.isEmpty() ? null : text.toString();
    }

    /** 供规格模板复用同一套字段取值（dev-20261030-006）。 */
    public static String tokenValue(InventoryMaterial material, MaterialAttributeSchema schema,
                                    Map<String, Object> attributes, MaterialSizeValue.SizeSpec spec, String field,
                                    String categoryName) {
        return tokenValue(material, schema, attributes, spec, field, categoryName, null);
    }

    /** 同上，可传品牌字典显示名（dev-20261105-004）。 */
    public static String tokenValue(InventoryMaterial material, MaterialAttributeSchema schema,
                                    Map<String, Object> attributes, MaterialSizeValue.SizeSpec spec, String field,
                                    String categoryName, String brandLabel) {
        return tokenValue(material, schema, attributes, spec, field, categoryName, brandLabel, null);
    }

    /** 同上，可传品牌缩写（规格模板用，dev-20261107-014）。 */
    public static String tokenValue(InventoryMaterial material, MaterialAttributeSchema schema,
                                    Map<String, Object> attributes, MaterialSizeValue.SizeSpec spec, String field,
                                    String categoryName, String brandLabel, String brandAbbr) {
        return tokenValue(material, schema, attributes, spec, field, categoryName, brandLabel, brandAbbr, null, null);
    }

    /** 同上，可再传供应商简称与缩写（dev-20261107-017）。 */
    public static String tokenValue(InventoryMaterial material, MaterialAttributeSchema schema,
                                    Map<String, Object> attributes, MaterialSizeValue.SizeSpec spec, String field,
                                    String categoryName, String brandLabel, String brandAbbr,
                                    String supplierShort, String supplierAbbr) {
        return valueOf(material, schema, attributes, spec, field, categoryName, brandLabel, null, brandAbbr,
                supplierShort, supplierAbbr);
    }

    private static String valueOf(InventoryMaterial material, MaterialAttributeSchema schema,
                                  Map<String, Object> attributes, MaterialSizeValue.SizeSpec spec, String field,
                                  String categoryName, String brandLabel, String colorLabel, String brandAbbr,
                                  String supplierShort, String supplierAbbr) {
        return switch (field) {
            case "brand" -> brandLabel == null || brandLabel.isBlank() ? material.getBrand() : brandLabel;
            case "brandabbr" -> brandAbbr == null || brandAbbr.isBlank()
                    ? (brandLabel == null || brandLabel.isBlank() ? material.getBrand() : brandLabel)
                    : brandAbbr;
            // 供应商：缩写 → 简称 → 全名，逐级回落（dev-20261107-017）
            case "supplierabbr" -> firstNonBlank(supplierAbbr, supplierShort, material.getSupplierName());
            case "suppliershort" -> firstNonBlank(supplierShort, material.getSupplierName());
            case "model" -> material.getModel();
            case "color" -> colorLabel == null || colorLabel.isBlank() ? material.getColor() : colorLabel;
            case "meta" -> material.getMaterialMeta();
            case "form" -> material.getMaterialForm();
            case "unit" -> material.getUnit();
            case "width" -> decimalText(firstNonNull(material.getWidthMm(), spec == null ? null : spec.widthMm()));
            case "length" -> decimalText(firstNonNull(material.getLengthMm(), spec == null ? null : spec.lengthMm()));
            case "thickness_mm" -> decimalText(firstNonNull(material.getThicknessMm(), spec == null ? null : spec.thicknessMm()));
            case "grammage" -> decimalText(material.getGrammage());
            // 档案标记：为真才输出文字，为假/空不输出（避免名称里出现“非客供”）
            case "customersupplied" -> Boolean.TRUE.equals(material.getIsCustomerSupplied()) ? "客供" : null;
            case "sampleonly" -> Boolean.TRUE.equals(material.getIsSample()) ? "样品" : null;
            case "category" -> categoryName;
            case "supplier" -> material.getSupplierName();
            default -> optionText(schema, attributes, field);
        };
    }

    public static String requireCategoryPrefix(String modelPrefix) {
        if (modelPrefix == null || modelPrefix.isBlank())
            throw new com.jjx.common.exception.BusinessException("品类型号前缀未配置，请先在物料分类中配置型号前缀");
        return modelPrefix;
    }

    private static java.math.BigDecimal firstNonNull(java.math.BigDecimal primary, java.math.BigDecimal fallback) {
        return primary != null ? primary : fallback;
    }

    /** 逐级回落取第一个非空文本（供应商缩写→简称→全名，dev-20261107-017）。 */
    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private static String decimalText(java.math.BigDecimal value) {
        if (value == null) return null;
        return value.stripTrailingZeros().toPlainString();
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
