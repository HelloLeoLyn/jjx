package com.jjx.production.enums;

import lombok.Getter;

/**
 * 工装模具状态枚举
 * 0=在库 1=使用中 2=清洗/保养中 3=维修中 4=报废
 */
@Getter
public enum ToolingStatusEnum {

    IN_STOCK(0, "在库"),
    IN_USE(1, "使用中"),
    CLEANING(2, "清洗/保养中"),
    REPAIRING(3, "维修中"),
    SCRAPPED(4, "报废");

    private final Integer code;
    private final String label;

    ToolingStatusEnum(Integer code, String label) {
        this.code = code;
        this.label = label;
    }

    public static ToolingStatusEnum fromCode(Integer code) {
        if (code == null) return null;
        for (ToolingStatusEnum e : values()) {
            if (e.code.equals(code)) return e;
        }
        return null;
    }

    public static String labelOf(Integer code) {
        ToolingStatusEnum e = fromCode(code);
        return e == null ? null : e.label;
    }
}
