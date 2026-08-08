package com.jjx.product.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 工序类别枚举
 */
@Getter
public enum ProcessCategoryEnum {
    
    PANEL("PANEL", "面板", "primary"),
    UP_LINE("UP_LINE", "上线", "primary"),
    DOWN_LINE("DOWN_LINE", "下线", "primary"),
    OTHER("OTHER", "其他", "info");

    private final String code;
    private final String name;
    private final String tagType;

    ProcessCategoryEnum(String code, String name, String tagType) {
        this.code = code;
        this.name = name;
        this.tagType = tagType;
    }

    private static final Map<String, ProcessCategoryEnum> CODE_MAP = 
        Arrays.stream(values()).collect(Collectors.toMap(ProcessCategoryEnum::getCode, e -> e));

    public static ProcessCategoryEnum getByCode(String code) {
        ProcessCategoryEnum category = CODE_MAP.get(code);
        if (category == null) {
            throw new IllegalArgumentException("无效的工序类别码: " + code);
        }
        return category;
    }

    public static boolean isValidCode(String code) {
        return code != null && CODE_MAP.containsKey(code);
    }
}