package com.jjx.production.enums;

import lombok.Getter;

/**
 * 质检类型枚举（P0-01 统一质检类型定义）
 * 正式类型：IQC 来料检验 / IPQC 过程检验 / FQC 完工检验 / OQC 出货检验
 * 注意：完工质检门（ProductionOrderServiceImpl）依赖 FQC，P3 Quality Integration 前保持稳定
 */
@Getter
public enum QualityInspectionTypeEnum {

    IQC("IQC", "来料检验"),
    IPQC("IPQC", "过程检验"),
    FQC("FQC", "完工检验"),
    OQC("OQC", "出货检验");

    private final String code;
    private final String label;

    QualityInspectionTypeEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /** 未知历史值返回 null（展示层原样回显，不抛异常） */
    public static QualityInspectionTypeEnum fromCode(String code) {
        if (code == null) return null;
        for (QualityInspectionTypeEnum e : values()) {
            if (e.code.equals(code)) return e;
        }
        return null;
    }

    /** 未知历史值原样返回（保持兼容） */
    public static String labelOf(String code) {
        QualityInspectionTypeEnum e = fromCode(code);
        return e == null ? code : e.label;
    }
}
