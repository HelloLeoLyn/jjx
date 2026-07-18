package com.jjx.inventory.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 预警级别枚举
 */
@Getter
@AllArgsConstructor
public enum AlertLevelEnum {

    INFO("info", "提示"),
    WARNING("warning", "警告"),
    URGENT("urgent", "紧急");

    private final String code;
    private final String label;

    public static AlertLevelEnum getByCode(String code) {
        for (AlertLevelEnum value : values()) {
            if (value.getCode().equals(code)) {
                return value;
            }
        }
        return null;
    }

}
