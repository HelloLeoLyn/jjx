package com.jjx.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 是否枚举
 */
@Getter
@AllArgsConstructor
public enum YesNoEnum {
    NO(0, "否"),
    YES(1, "是");

    private final Integer code;
    private final String label;

    public static YesNoEnum getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (YesNoEnum value : values()) {
            if (value.getCode().equals(code)) {
                return value;
            }
        }
        return null;
    }

    public boolean isYes() {
        return this == YES;
    }

    /**
     * @deprecated 使用 { #getLabel()}
     */
    @Deprecated
    public String getDesc() {
        return label;
    }
}
