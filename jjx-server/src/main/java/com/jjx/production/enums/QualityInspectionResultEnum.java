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

    /** dev-20260928-006：唯一真源；比较一律走这里，别再写字面量。 */
    public static boolean isPass(String code) {
        return PASS.code.equalsIgnoreCase(code == null ? "" : code.trim());
    }

    /** dev-20260928-006：唯一真源；比较一律走这里，别再写字面量。 */
    public static boolean isFail(String code) {
        return FAIL.code.equalsIgnoreCase(code == null ? "" : code.trim());
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
