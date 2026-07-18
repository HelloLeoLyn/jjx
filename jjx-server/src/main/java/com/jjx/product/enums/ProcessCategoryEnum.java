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
    
    PREPARATION("PREPARATION", "准备", "info"),
    MAIN("MAIN", "主要", "primary"),
    FINISHING("FINISHING", "后处理", "warning"),
    QUALITY("QUALITY", "质量", "success");

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
}