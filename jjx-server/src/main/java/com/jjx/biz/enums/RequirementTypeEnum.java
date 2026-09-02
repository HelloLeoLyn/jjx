package com.jjx.biz.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 业务需求类型
 */
@Getter
@AllArgsConstructor
public enum RequirementTypeEnum {
    CHANGE("CHANGE", "变更"),
    ADD("ADD", "新增"),
    IMPROVE("IMPROVE", "改善"),
    ISSUE("ISSUE", "问题");

    private final String value;
    private final String label;

    public static RequirementTypeEnum getByValue(String value) {
        if (value == null) return null;
        for (RequirementTypeEnum t : values()) {
            if (t.value.equals(value)) return t;
        }
        return null;
    }
}
