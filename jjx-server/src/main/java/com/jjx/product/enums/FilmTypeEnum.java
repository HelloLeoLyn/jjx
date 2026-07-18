package com.jjx.product.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 菲林类型枚举
 */
@Getter
public enum FilmTypeEnum {
    
    OVERLAY("OVERLAY", "面板菲林", 1),
    UPPER_CIRCUIT("UPPER_CIRCUIT", "上层线路菲林", 2),
    SPACER("SPACER", "间隔菲林", 3),
    LOWER_CIRCUIT("LOWER_CIRCUIT", "下层线路菲林", 4),
    BACK_ADHESIVE("BACK_ADHESIVE", "背胶菲林", 5);

    private final String code;
    private final String name;
    private final Integer order;

    FilmTypeEnum(String code, String name, Integer order) {
        this.code = code;
        this.name = name;
        this.order = order;
    }

    private static final Map<String, FilmTypeEnum> CODE_MAP = 
        Arrays.stream(values()).collect(Collectors.toMap(FilmTypeEnum::getCode, e -> e));

    public static FilmTypeEnum fromCode(String code) {
        FilmTypeEnum type = CODE_MAP.get(code);
        if (type == null) {
            throw new IllegalArgumentException("无效的菲林类型: " + code);
        }
        return type;
    }

    public static String getNameByCode(String code) {
        FilmTypeEnum type = fromCode(code);
        return type != null ? type.getName() : "未知";
    }
}