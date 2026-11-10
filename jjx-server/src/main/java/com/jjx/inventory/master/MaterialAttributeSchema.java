package com.jjx.inventory.master;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.common.exception.BusinessException;

import java.math.BigDecimal;
import java.util.*;

/** 品类属性契约；候选使用稳定代码，单位由定义固定。dev-20261024-001 */
public record MaterialAttributeSchema(
        int version,
        @JsonProperty("meta_options") List<String> metaOptions,
        @JsonProperty("meta_required") boolean metaRequired,
        @JsonProperty("default_meta") String defaultMeta,
        @JsonProperty("default_form") String defaultForm,
        @JsonProperty("default_unit") String defaultUnit,
        List<Field> fields,
        /** 命名规则：勾选参与命名的字段与顺序；为空表示未配置。 */
        @JsonProperty("name_pattern") MaterialNamingPattern namePattern,
        /** 规格模板：字段顺序 + 每字段 1–3 字符分隔符；为空时回落旧拼接（dev-20261029-027）。 */
        @JsonProperty("spec_pattern") SpecPattern specPattern,
        @JsonProperty("model_pattern") MaterialNamingPattern modelPattern) {

    /** 保持既有命名/规格调用的构造方式。 */
    public MaterialAttributeSchema(int version, List<String> metaOptions, boolean metaRequired, String defaultMeta,
                                   String defaultForm, String defaultUnit, List<Field> fields,
                                   MaterialNamingPattern namePattern, SpecPattern specPattern) {
        this(version, metaOptions, metaRequired, defaultMeta, defaultForm, defaultUnit, fields, namePattern, specPattern, null);
    }

    public MaterialAttributeSchema(int version, List<String> metaOptions, boolean metaRequired, String defaultMeta,
                                   String defaultForm, String defaultUnit, List<Field> fields,
                                   MaterialNamingPattern namePattern) {
        this(version, metaOptions, metaRequired, defaultMeta, defaultForm, defaultUnit, fields, namePattern, null);
    }

    /** 规格模板：showLabel=是否输出“字段名:”，withUnit=是否带单位。 */
    /** unit_position：each=每项带单位 / end=只在末尾带一次 / none=不带单位（缺省按 with_unit 兼容旧数据）。 */
    public record SpecPattern(List<SpecField> fields,
                              @JsonProperty("show_label") boolean showLabel,
                              @JsonProperty("with_unit") boolean withUnit,
                              @JsonProperty("unit_position") String unitPosition) {
        public record SpecField(String key, String sep, String unit,
                                @JsonDeserialize(using = ScaleDeserializer.class) Integer scale) {
            public SpecField(String key, String sep) { this(key, sep, null, null); }
        }

        /** 禁止 Jackson 将 1.5 小数位静默截断成 1。 */
        public static final class ScaleDeserializer extends JsonDeserializer<Integer> {
            @Override public Integer deserialize(JsonParser parser, DeserializationContext context) throws java.io.IOException {
                if (!parser.isExpectedNumberIntToken()) throw new BusinessException("尺寸显示小数位须为0至6的整数");
                return parser.getIntValue();
            }
        }
    }

    public record Option(String code, String label) { }
    public record Field(String key, String label, String type, String unit, boolean required,
                        List<Option> options, BigDecimal min, BigDecimal max, Integer precision,
                        @JsonProperty("default") JsonNode defaultValue,
                        @JsonProperty("visible_when") Map<String, String> visibleWhen,
                        Boolean common, @JsonProperty("in_spec") Boolean inSpec,
                        @JsonProperty("spec_order") Integer specOrder,
                        @JsonProperty("true_label") String trueLabel,
                        @JsonProperty("false_label") String falseLabel) { }

    public static final Set<String> DIMENSION_KEYS = Set.of("width", "length", "thickness_mm");
    public static final Set<String> DIMENSION_UNITS = Set.of("mm", "cm", "m", "μm");

    public static final Set<String> TYPES = Set.of("enum", "multienum", "string", "integer", "decimal", "boolean");
    /** 属性单位白名单：无量纲留空；避免 mm/MM/毫米 混写。 */
    public static final Set<String> UNITS = Set.of("%", "g/m²", "g", "kg/m³", "mm", "μm", "N", "gf", "℃", "s");

    public static MaterialAttributeSchema parse(String json, ObjectMapper mapper) {
        try {
            MaterialAttributeSchema schema = mapper.readValue(json, MaterialAttributeSchema.class);
            schema.validateDefinition();
            return schema;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("属性模板格式错误");
        }
    }

    public void validateDefinition() {
        if (version < 1 || metaOptions == null || fields == null || fields.size() > 100) fail("模板版本、材质候选或字段定义无效");
        if (new HashSet<>(metaOptions).size() != metaOptions.size()) fail("模板材质候选重复");
        if (metaRequired && metaOptions.isEmpty()) fail("材质必填时必须配置材质候选");
        if (defaultMeta != null && !metaOptions.contains(defaultMeta)) fail("默认材质不在候选范围");
        if (namePattern != null) namePattern.validate(this);
        if (modelPattern != null) modelPattern.validateModel(this);
        if (specPattern != null && specPattern.fields() != null) {
            // 规格模板与命名规则共用同一份可选项清单（dev-20261030-006）
            Set<String> specKeys = new HashSet<>(MaterialNamingPattern.COMMON_TOKENS);
            fields.forEach(field -> specKeys.add(field.key()));
            for (SpecPattern.SpecField item : specPattern.fields()) {
                if (item == null || item.key() == null || item.key().isBlank()) continue;
                MaterialNamingPattern.validatedSeparator(item.sep());
                if (!specKeys.contains(item.key().trim())) fail("规格模板含未知字段：" + item.key());
                if ((item.unit() != null || item.scale() != null) && !DIMENSION_KEYS.contains(item.key().trim()))
                    fail("只有宽、长、厚允许配置显示单位和小数位：" + item.key());
                if (item.unit() != null && !DIMENSION_UNITS.contains(item.unit())) fail("尺寸显示单位只允许 mm/cm/m/μm");
                if (item.scale() != null && (item.scale() < 0 || item.scale() > 6)) fail("尺寸显示小数位须为0至6");
            }
        }
        Set<String> keys = new HashSet<>();
        Set<String> labels = new HashSet<>();
        for (Field f : fields) {
            if (f == null) fail("属性定义不能为空");
            if (f.key == null || !f.key.matches("[a-z][a-z0-9_]{0,49}") || !keys.add(f.key)) fail("属性编码无效或重复");
            if (f.label == null || f.label.isBlank() || !labels.add(f.label) || f.type == null || !TYPES.contains(f.type)) fail("属性名称重复或类型无效：" + f.key);
            if (f.unit != null && !f.unit.isBlank() && !UNITS.contains(f.unit.trim())) fail("属性单位不在允许范围：" + f.label + "（" + f.unit + "）");
            if (f.min != null && f.max != null && f.min.compareTo(f.max) > 0) fail("属性数值范围错误：" + f.label);
            if (f.precision != null && (f.precision < 0 || f.precision > 8)) fail("属性精度范围为0至8：" + f.label);
            if (f.specOrder != null && (f.specOrder < 0 || f.specOrder > 10000)) fail("规格排序须为0至10000：" + f.label);
            if (f.trueLabel != null && f.trueLabel.length() > 50 || f.falseLabel != null && f.falseLabel.length() > 50) fail("布尔显示文字不能超过50字：" + f.label);
            if (f.type.equals("enum") || f.type.equals("multienum")) {
                if (f.options == null || f.options.isEmpty()) fail("枚举缺少选项：" + f.label);
                Set<String> codes = new HashSet<>();
                Set<String> optionLabels = new HashSet<>();
                for (Option o : f.options) {
                    if (o == null || o.code == null || o.code.isBlank() || o.label == null || o.label.isBlank() || !codes.add(o.code) || !optionLabels.add(o.label)) fail("枚举选项无效或重复：" + f.label);
                }
            }
            if (f.defaultValue != null && !f.defaultValue.isNull()) validateValue(f, f.defaultValue);
        }
        for (Field f : fields) {
            if (f.visibleWhen != null) {
                for (String key : f.visibleWhen.keySet()) {
                    if (!keys.contains(key) || key.equals(f.key)) fail("属性显示条件引用无效：" + f.label);
                    Field dependency = fields.stream().filter(d -> key.equals(d.key)).findFirst().orElseThrow();
                    if (dependency.visibleWhen != null && !dependency.visibleWhen.isEmpty()) fail("属性显示条件只允许引用无条件字段");
                }
            }
        }
    }

    /** 返回归一后的新对象；不保留未知字段、不把未知事实补成false。 */
    public Map<String, Object> validateAttributes(Map<String, Object> input, ObjectMapper mapper) {
        Map<String, Object> supplied = input == null ? Map.of() : input;
        Set<String> allowed = new HashSet<>();
        fields.forEach(f -> allowed.add(f.key));
        for (String key : supplied.keySet()) if (!allowed.contains(key)) fail("该品类不支持属性：" + key);
        Map<String, Object> candidate = new LinkedHashMap<>(supplied);
        for (Field f : fields) {
            if (!candidate.containsKey(f.key) && f.defaultValue != null && !f.defaultValue.isNull()) candidate.put(f.key, mapper.convertValue(f.defaultValue, Object.class));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Field f : fields) {
            JsonNode value = mapper.valueToTree(candidate.get(f.key));
            boolean missing = value == null || value.isNull() || value.isTextual() && value.asText().isBlank() || value.isArray() && value.isEmpty();
            boolean visible = visible(f, candidate);
            if (!visible && !missing) fail("属性不适用于当前选项：" + f.label);
            if (visible && f.required && missing && !f.key.equals("thickness_mm")) fail(f.label + "不能为空");
            if (!missing) {
                validateValue(f, value);
                result.put(f.key, mapper.convertValue(value, Object.class));
            }
        }
        return result;
    }

    public static boolean visible(Field f, Map<String, Object> values) {
        return f.visibleWhen == null || f.visibleWhen.entrySet().stream()
                .allMatch(e -> e.getValue().equals(String.valueOf(values.get(e.getKey()))));
    }

    private static void validateValue(Field f, JsonNode value) {
        switch (f.type) {
            case "boolean" -> { if (!value.isBoolean()) fail(f.label + "必须为是或否"); }
            case "string" -> { if (!value.isTextual() || value.asText().length() > 200) fail(f.label + "必须为不超过200字的文本"); }
            case "enum" -> { if (!value.isTextual() || !optionCodes(f).contains(value.asText())) fail(f.label + "不在允许选项内"); }
            case "multienum" -> {
                if (!value.isArray()) fail(f.label + "必须为选项数组");
                Set<String> chosen = new HashSet<>();
                for (JsonNode item : value) if (!item.isTextual() || !optionCodes(f).contains(item.asText()) || !chosen.add(item.asText())) fail(f.label + "包含无效或重复选项");
            }
            case "integer", "decimal" -> {
                if (!value.isNumber()) fail(f.label + "必须为数值");
                BigDecimal number = value.decimalValue();
                if (f.type.equals("integer") && number.stripTrailingZeros().scale() > 0) fail(f.label + "必须为整数");
                if (number.precision() - number.scale() > 12 || number.stripTrailingZeros().scale() > (f.precision == null ? 4 : f.precision)) fail(f.label + "数值精度超限");
                if (f.min != null && number.compareTo(f.min) < 0 || f.max != null && number.compareTo(f.max) > 0) fail(f.label + "超出允许范围");
            }
            default -> fail("未知属性类型");
        }
    }

    private static Set<String> optionCodes(Field f) {
        Set<String> codes = new HashSet<>();
        f.options.forEach(o -> codes.add(o.code));
        return codes;
    }

    public boolean hasSpecPattern() {
        return specPattern != null && specPattern.fields() != null && !specPattern.fields().isEmpty();
    }

    /** 规格文本：配了 spec_pattern 用模板，否则回落旧拼接（dev-20261029-027）。 */
    /** 规格文本：字段取值与命名规则共用 MaterialNamingPattern 的注册表（dev-20261030-006）。 */
    public String specification(com.jjx.inventory.domain.InventoryMaterial material,
                                Map<String, Object> attributes, String categoryName) {
        java.math.BigDecimal widthMm = material == null ? null : material.getWidthMm();
        java.math.BigDecimal lengthMm = material == null ? null : material.getLengthMm();
        java.math.BigDecimal thicknessMm = material == null ? null : material.getThicknessMm();
        if (specPattern == null || specPattern.fields() == null || specPattern.fields().isEmpty()) {
            return compose(attributes, thicknessMm == null ? null : thicknessMm.stripTrailingZeros().toPlainString(),
                    MaterialSizeValue.render(widthMm, lengthMm, thicknessMm, null));
        }
        Map<String, Object> values = new LinkedHashMap<>(attributes);
        StringBuilder text = new StringBuilder();
        boolean first = true;
        String lastUnit = "";
        String position = specPattern.unitPosition() == null || specPattern.unitPosition().isBlank()
                ? (specPattern.withUnit() ? "each" : "none") : specPattern.unitPosition().trim().toLowerCase();
        boolean configuredDimensions = specPattern.fields().stream().filter(Objects::nonNull)
                .anyMatch(field -> field.key() != null && DIMENSION_KEYS.contains(field.key()) && (field.unit() != null || field.scale() != null));
        MaterialSizeValue.SizeSpec spec = material == null ? null : MaterialSizeValue.spec(material.getSize());
        for (SpecPattern.SpecField item : specPattern.fields()) {
            if (item == null || item.key() == null || item.key().isBlank()) continue;
            String key = item.key().trim();
            String display;
            String unit = specUnit(key);
            if (DIMENSION_KEYS.contains(key) && configuredDimensions) {
                BigDecimal value = switch (key) {
                    case "width" -> widthMm;
                    case "length" -> lengthMm;
                    default -> thicknessMm;
                };
                // 库中结构化字段是 mm；不从紧凑尺寸文本反推缺失维度。
                if (value == null) continue;
                var segment = MaterialSizeValue.segment(value, item.unit(), item.scale());
                display = segment.value();
                if (item.unit() != null) unit = segment.unit();
            } else display = MaterialNamingPattern.tokenValue(material, this, values, spec, key, categoryName);
            if (display == null || display.isBlank()) continue;
            if (specPattern.showLabel()) display = specLabel(key) + ":" + display;
            if ("each".equals(position)) display += unit;
            lastUnit = unit;
            String sep = item.sep() == null ? "；" : MaterialNamingPattern.validatedSeparator(item.sep());
            if (!first) text.append(sep);
            text.append(display);
            first = false;
        }
        if ("end".equals(position) && !first && !lastUnit.isEmpty()) text.append(lastUnit);
        String result = text.toString();
        if (result.length() > 500) fail("生成规格超过500字，请精简属性内容");
        return result.isEmpty() ? null : result;
    }

    private String specLabel(String key) {
        return switch (key) {
            case "width" -> "宽";
            case "length" -> "长";
            case "thickness_mm" -> "厚";
            default -> fields.stream().filter(f -> f.key().equals(key)).map(Field::label).findFirst().orElse(key);
        };
    }

    private String specUnit(String key) {
        if ("width".equals(key) || "length".equals(key) || "thickness_mm".equals(key))
            return "mm";
        return fields.stream().filter(f -> f.key().equals(key)).map(Field::unit)
                .filter(u -> u != null && !u.isBlank()).findFirst().orElse("");
    }

    public String compose(Map<String, Object> values) {
        return compose(values, null, null);
    }

    /** 与前端预览同口径：模板顺序、可见属性、厚度列兜底、尺寸固定mm。 */
    public String compose(Map<String, Object> attributes, String thickness, String widthSize) {
        Map<String, Object> values = new LinkedHashMap<>(attributes);
        if (thickness != null && !thickness.isBlank()) {
            fields.stream().filter(f -> f.key.equals("thickness_mm") || f.key.equals("gram_weight_gsm"))
                    .findFirst().ifPresent(f -> values.putIfAbsent(f.key, thickness));
        }
        List<String> parts = new ArrayList<>();
        for (Field f : fields) {
            Object value = values.get(f.key);
            if (!visible(f, values) || value == null || value.toString().isBlank()
                    || value instanceof List<?> list && list.isEmpty()) continue;
            String display;
            if (value instanceof Boolean b) display = b ? "是" : "否";
            else if (f.type.equals("enum")) display = label(f, value.toString());
            else if (value instanceof List<?> list) display = String.join("、", list.stream().map(v -> label(f, v.toString())).toList());
            else if (value instanceof Number number) display = new BigDecimal(number.toString()).stripTrailingZeros().toPlainString();
            else display = value.toString();
            String unit = f.unit == null ? "" : f.unit;
            parts.add(f.label + ":" + display + unit);
        }
        if (widthSize != null && !widthSize.isBlank()) {
            String size = MaterialSizeValue.normalize(widthSize.trim());
            parts.add("尺寸:" + size + (size.matches("(?i).*(?:mm|cm|m)$") ? "" : "mm"));
        }
        String text = String.join("；", parts);
        if (text.length() > 500) fail("生成规格超过500字，请精简属性内容");
        return text;
    }

    private static String label(Field f, String code) {
        return f.options.stream().filter(o -> o.code.equals(code)).map(Option::label).findFirst().orElse(code);
    }
    private static void fail(String message) { throw new BusinessException(message); }
}
