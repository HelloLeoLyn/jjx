package com.jjx.quality.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 检验批状态（dev-20260917-001）
 * 待检 → 检验中 → 已判定 → 已关闭（不良全部处置完毕/已入库完毕）
 */
@Getter
@AllArgsConstructor
public enum QualityLotStatusEnum {

    PENDING("PENDING", "待检"),
    INSPECTING("INSPECTING", "检验中"),
    JUDGED("JUDGED", "已判定"),
    CLOSED("CLOSED", "已关闭");

    private final String code;
    private final String label;

    public static String labelOf(String code) {
        for (QualityLotStatusEnum value : values()) {
            if (value.code.equals(code)) {
                return value.label;
            }
        }
        return code;
    }
}
