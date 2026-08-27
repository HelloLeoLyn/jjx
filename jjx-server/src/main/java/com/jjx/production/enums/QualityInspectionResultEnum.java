package com.jjx.production.enums;

import lombok.Getter;

/**
 * 质检结果枚举（P0-01 统一质检结果定义）
 * 正式结果：PENDING 待检 / PASS 合格 / FAIL 不合格
 */
@Getter
public enum QualityInspectionResultEnum {

    PENDING("pending", "待检"),
    PASS("pass", "合格"),
    FAIL("fail", "不合格");

    private final String code;
    private final String label;

    QualityInspectionResultEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /** 未知历史值返回 null（展示层原样回显，不抛异常） */
    public static QualityInspectionResultEnum fromCode(String code) {
        if (code == null) return null;
        for (QualityInspectionResultEnum e : values()) {
            if (e.code.equals(code)) return e;
        }
        return null;
    }

    /** 未知历史值原样返回（保持兼容） */
    public static String labelOf(String code) {
        QualityInspectionResultEnum e = fromCode(code);
        return e == null ? code : e.label;
    }
}
