package com.jjx.inventory.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 通用工单补料来源类型（inventory_outbound_order.supplement_reason_type）—— dev-20260924-002 / dev-20260923-025。
 *
 * <p>与「不良原因」（检验项目 + CR/MA/MI，见 dev-20260924-004）**不是一个东西**：
 * 这里说的是"为什么又要领料"，不是"产品为什么不良"。两者并存、各管一段。</p>
 */
@Getter
@AllArgsConstructor
public enum ProductionSupplementReasonEnum {

    /** 生产超耗/现场缺料（货已做出来，不补产） */
    PRODUCTION_OVERUSE("PRODUCTION_OVERUSE", "生产超耗/现场缺料"),

    /** 报废后补产（关联不良单，需同时生成补产任务） */
    SCRAP_REPLENISHMENT("SCRAP_REPLENISHMENT", "报废后补产"),

    /** 试制调机（工艺试验/调机耗料） */
    TRIAL_ADJUSTMENT("TRIAL_ADJUSTMENT", "试制调机"),

    /** 来料不良（来料缺陷导致补料） */
    INCOMING_DEFECT("INCOMING_DEFECT", "来料不良");

    private final String code;

    private final String label;

    public static ProductionSupplementReasonEnum getByCode(String code) {
        if (code == null) {
            return null;
        }
        for (ProductionSupplementReasonEnum value : values()) {
            if (value.code.equalsIgnoreCase(code.trim())) {
                return value;
            }
        }
        return null;
    }

    public static String labelOf(String code) {
        ProductionSupplementReasonEnum value = getByCode(code);
        return value == null ? code : value.label;
    }

    /** 是否需要关联质量不良单（且需要补产任务） */
    public static boolean requiresNcr(String code) {
        return SCRAP_REPLENISHMENT.code.equalsIgnoreCase(code == null ? "" : code.trim());
    }
}
