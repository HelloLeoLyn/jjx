package com.jjx.product.enums;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 规范分色检查表单项自查结论；空字符串表示未检查（打印留空）。
 */
public enum ColorCheckResultEnum {

    CORRECT("CORRECT", "正确"),
    INCORRECT("INCORRECT", "错误"),
    NA("NA", "不适用");

    private final String code;
    private final String label;

    ColorCheckResultEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    private static final Map<String, String> LABELS;

    static {
        Map<String, String> labels = new LinkedHashMap<>();
        for (ColorCheckResultEnum item : values()) labels.put(item.code, item.label);
        LABELS = Collections.unmodifiableMap(labels);
    }

    public static Map<String, String> labels() {
        return LABELS;
    }

    /** 空字符串（未检查）视为合法；其余必须是已定义的结论。 */
    public static boolean isValid(String code) {
        return code == null || code.isEmpty() || LABELS.containsKey(code);
    }

    public static String label(String code) {
        return code == null || code.isEmpty() ? "未检查" : LABELS.getOrDefault(code, code);
    }
}
