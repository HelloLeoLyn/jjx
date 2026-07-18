package com.jjx.product.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 工序类型枚举
 */
@Getter
public enum ProcessTypeEnum {
    MAIN_PAD("MAIN_PAD", "面板", "primary"),
    UP_LINE("UP_LINE", "上线", "primary"),
    DOWN_LINE("DOWN_LINE", "下线", "primary"),
    PRINTING("PRINTING", "印刷", "primary"),
    CUTTING("CUTTING", "模切", "success"),
    LAMINATING("LAMINATING", "贴合", "warning"),
    TESTING("TESTING", "测试", "info"),
    PACKAGING("PACKAGING", "包装", "danger");

    private final String code;
    private final String name;
    private final String tagType;

    ProcessTypeEnum(String code, String name, String tagType) {
        this.code = code;
        this.name = name;
        this.tagType = tagType;
    }

    private static final Map<String, ProcessTypeEnum> CODE_MAP = 
        Arrays.stream(values()).collect(Collectors.toMap(ProcessTypeEnum::getCode, e -> e));

    public static ProcessTypeEnum getByCode(String code) {
        ProcessTypeEnum type = CODE_MAP.get(code);
        if (type == null) {
            throw new IllegalArgumentException("无效的工序类型码: " + code);
        }
        return type;
    }

    public static boolean isValidCode(String code) {
        return CODE_MAP.containsKey(code);
    }
}