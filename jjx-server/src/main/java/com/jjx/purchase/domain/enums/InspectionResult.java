package com.jjx.purchase.domain.enums;

import lombok.Getter;

/**
 * 检验结果枚举
 */
@Getter
public enum InspectionResult {

    /**
     * 合格
     */
    PASSED("passed", "合格"),

    /**
     * 不合格
     */
    FAILED("failed", "不合格");

    private final String code;
    private final String description;

    InspectionResult(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据code获取枚举
     */
    public static InspectionResult getByCode(String code) {
        for (InspectionResult result : values()) {
            if (result.getCode().equals(code)) {
                return result;
            }
        }
        return null;
    }

    /**
     * 判断是否为合格
     */
    public boolean isPassed() {
        return this == PASSED;
    }
}
