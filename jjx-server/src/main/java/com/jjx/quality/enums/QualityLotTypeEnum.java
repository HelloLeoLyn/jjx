package com.jjx.quality.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 检验批类型（质量管理统一模型，dev-20260917-001）
 */
@Getter
@AllArgsConstructor
public enum QualityLotTypeEnum {

    IQC("IQC", "来料检验"),
    IPQC("IPQC", "过程检验"),
    FQC("FQC", "成品检验");

    private final String code;
    private final String label;

    public static QualityLotTypeEnum getByCode(String code) {
        for (QualityLotTypeEnum value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }

    public static String labelOf(String code) {
        QualityLotTypeEnum value = getByCode(code);
        return value == null ? code : value.label;
    }
}
