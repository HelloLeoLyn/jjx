package com.jjx.production.enums;

import lombok.Getter;

/**
 * 工装模具类型枚举
 * SCREEN=网框（丝印网版） DIE=刀模（模切模具）
 */
@Getter
public enum ToolingTypeEnum {

    SCREEN("SCREEN", "网框"),
    DIE("DIE", "刀模");

    private final String code;
    private final String label;

    ToolingTypeEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /**
     * 根据编码获取枚举，未知编码返回 null
     */
    public static ToolingTypeEnum fromCode(String code) {
        if (code == null) return null;
        for (ToolingTypeEnum e : values()) {
            if (e.code.equalsIgnoreCase(code.trim())) return e;
        }
        return null;
    }

    /**
     * 中文标签转枚举（导入用）：网框/刀模/SCREEN/DIE 均可识别
     */
    public static ToolingTypeEnum fromLabel(String label) {
        if (label == null) return null;
        String s = label.trim();
        for (ToolingTypeEnum e : values()) {
            if (e.label.equals(s) || e.code.equalsIgnoreCase(s)) return e;
        }
        return null;
    }
}
