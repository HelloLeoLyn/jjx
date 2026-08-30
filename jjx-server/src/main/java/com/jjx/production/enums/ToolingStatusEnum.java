package com.jjx.production.enums;

import com.jjx.common.enums.BizStatusEnum;

import lombok.Getter;

/**
 * 工装模具状态枚举
 * 0=在库 1=使用中 2=清洗/保养中 3=维修中 4=报废
 */
@Getter
public enum ToolingStatusEnum implements BizStatusEnum {

    IN_STOCK(0, "在库"),
    IN_USE(1, "使用中"),
    CLEANING(2, "清洗/保养中"),
    REPAIRING(3, "维修中"),
    SCRAPPED(4, "报废");

    private final Integer value;
    private final String label;

    ToolingStatusEnum(Integer value, String label) {
        this.value = value;
        this.label = label;
    }

    public static ToolingStatusEnum fromCode(Integer value) {
        if (value == null) return null;
        for (ToolingStatusEnum e : values()) {
            if (e.value.equals(value)) return e;
        }
        return null;
    }

    public static String labelOf(Integer value) {
        ToolingStatusEnum e = fromCode(value);
        return e == null ? null : e.label;
    }
}
