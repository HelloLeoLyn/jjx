package com.jjx.quality.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 不良件状态（quality_ncr_piece.status）—— dev-20260924-004。
 *
 * <p>件是**处置的最小单位**（一件一个决定）；件级只做身份与追溯，不参与库存数量计算。</p>
 */
@Getter
@AllArgsConstructor
public enum QualityPieceStatusEnum {

    /** 待处置（判定出不良即为此状态；业内在成品侧等价于「隔离中」） */
    PENDING("PENDING", "待处置"),

    /** 返工中（已挂到返工处置单） */
    REWORKING("REWORKING", "返工中"),

    /** 返工回收（返工复检合格，随批入库；与「有效合格累计」对账，见 dev-20260922-018） */
    RECOVERED("RECOVERED", "已回收"),

    /** 已报废（口径A：不良品从未进良品库，不产生库存扣减） */
    SCRAPPED("SCRAPPED", "已报废"),

    /** 让步放行（特采，客户确认后转良品库存） */
    CONCEDED("CONCEDED", "让步放行"),

    /** 退货（成品侧入口后续开；来料侧已用） */
    RETURNED("RETURNED", "已退货"),

    /** 随批作废（来源批被后继复检版本取代 / 撤销后失效） */
    VOID("VOID", "已作废");

    private final String code;

    private final String label;

    public static String labelOf(String code) {
        for (QualityPieceStatusEnum value : values()) {
            if (value.code.equals(code)) {
                return value.label;
            }
        }
        return code;
    }
}
