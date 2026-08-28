package com.jjx.production.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum QualityTemplateStatusEnum {
    DRAFT(0, "草稿"),
    ACTIVE(1, "生效"),
    DISABLED(2, "停用");

    private final int code;
    private final String label;

    public static QualityTemplateStatusEnum fromCode(Integer code) {
        if (code == null) return null;
        for (QualityTemplateStatusEnum value : values()) {
            if (value.code == code) return value;
        }
        return null;
    }
}
