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
    PRINTING("PRINTING", "印刷", "primary"),
    PUNCH_HOLE("PUNCH_HOLE", "冲孔", "primary"),
    PUNCH_SHAPE("PUNCH_SHAPE", "冲型", "primary"),
    LAMINATING("LAMINATING", "贴合", "warning"),
    CUTTING("CUTTING", "裁切", "success"),
    GASKET("GASKET", "垫片", "info"),
    PROTECTIVE_FILM("PROTECTIVE_FILM", "保护膜", "info"),
    SPACER("SPACER", "隔片", "info"),
    CLEANING("CLEANING", "清洁", "info"),
    FILM_APPLY("FILM_APPLY", "贴膜", "primary"),
    FILM_REMOVE("FILM_REMOVE", "撕膜", "warning"),
    RESISTOR("RESISTOR", "电阻", "danger"),
    CONNECTOR("CONNECTOR", "连接器", "primary"),
    QC("QC", "品检", "success"),
    PANEL("PANEL", "面板", "primary"),
    UP_LINE("UP_LINE", "上线", "primary"),
    DOWN_LINE("DOWN_LINE", "下线", "primary"),
    OTHER("OTHER", "其他", "info");

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