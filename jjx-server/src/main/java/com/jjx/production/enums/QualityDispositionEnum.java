package com.jjx.production.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum QualityDispositionEnum {
    RETURN("RETURN", "退货", false),
    SUPPLIER_REWORK("SUPPLIER_REWORK", "供应商来厂重工", false),
    INTERNAL_SORT("INTERNAL_SORT", "内部挑选/返工", false),
    PARTIAL_ACCEPT("PARTIAL_ACCEPT", "部分接收", true),
    CONCESSION("CONCESSION", "让步接收", true),
    SCRAP("SCRAP", "报废", false),
    REINSPECT("REINSPECT", "待复检", false),
    HOLD("HOLD", "待定/隔离", false);

    private final String code;
    private final String label;
    private final boolean accepted;

    public static QualityDispositionEnum fromCode(String code) {
        if (code == null) return null;
        for (QualityDispositionEnum value : values()) {
            if (value.code.equalsIgnoreCase(code)) return value;
        }
        return null;
    }
}
