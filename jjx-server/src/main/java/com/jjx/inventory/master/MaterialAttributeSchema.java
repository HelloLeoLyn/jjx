package com.jjx.inventory.master;

import com.fasterxml.jackson.annotation.JsonProperty;
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
        List<Field> fields) {

    public record Option(String code, String label) { }
    public record Field(String key, String label, String type, String unit, boolean required,
                        List<Option> options, BigDecimal min, BigDecimal max, Integer precision,
                        @JsonProperty("default") JsonNode defaultValue,
                        @JsonProperty("visible_when") Map<String, String> visibleWhen) { }

    public static final Set<String> TYPES = Set.of("enum", "multienum", "string", "integer", "decimal", "boolean");

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
        Set<String> keys = new HashSet<>();
        Set<String> labels = new HashSet<>();
        for (Field f : fields) {
            if (f == null) fail("属性定义不能为空");
            if (f.key == null || !f.key.matches("[a-z][a-z0-9_]{0,49}") || !keys.add(f.key)) fail("属性编码无效或重复");
            if (f.label == null || f.label.isBlank() || !labels.add(f.label) || f.type == null || !TYPES.contains(f.type)) fail("属性名称重复或类型无效：" + f.key);
            if (f.min != null && f.max != null && f.min.compareTo(f.max) > 0) fail("属性数值范围错误：" + f.label);
            if (f.precision != null && (f.precision < 0 || f.precision > 8)) fail("属性精度范围为0至8：" + f.label);
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
            if (visible && f.required && missing) fail(f.label + "不能为空");
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

    public String compose(Map<String, Object> values) {
        List<String> parts = new ArrayList<>();
        for (Field f : fields) {
            Object value = values.get(f.key);
            if (value == null) continue;
            String display;
            if (value instanceof Boolean b) display = b ? "是" : "否";
            else if (f.type.equals("enum")) display = label(f, value.toString());
            else if (value instanceof List<?> list) display = String.join("、", list.stream().map(v -> label(f, v.toString())).toList());
            else display = value.toString();
            parts.add(f.label + ":" + display + (f.unit == null ? "" : f.unit));
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
