package com.jjx.production.enums;

import lombok.Getter;

/**
 * 生产报工状态枚举（P3：WorkReport + Approval 正式状态机）
 * PENDING：已提交，等待审批（占用 Task 可报工额度，不计 completed）
 * APPROVED：审批通过（正式有效完成事实，计入 completedQuantity）
 * REJECTED：审批驳回（不形成有效完成，释放原占用，历史保留）
 * CANCELLED：已撤销（不形成有效完成，释放原占用，历史保留）
 * <p>
 * P3 删除旧 SUBMITTED；禁止把 SUBMITTED 当作 completed。
 * 历史事实不可覆盖：更正 = CANCELLED/REJECTED + 新建正确报工；禁止物理删除。
 */
@Getter
public enum WorkReportStatusEnum {

    PENDING("PENDING", "待审批"),
    APPROVED("APPROVED", "已通过"),
    REJECTED("REJECTED", "已驳回"),
    CANCELLED("CANCELLED", "已撤销");

    private final String code;
    private final String label;

    WorkReportStatusEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public static WorkReportStatusEnum fromCode(String code) {
        if (code == null) return null;
        for (WorkReportStatusEnum e : values()) {
            if (e.code.equals(code)) return e;
        }
        return null;
    }

    /** 未知/历史值原样返回（兼容展示，不抛异常） */
    public static String labelOf(String code) {
        WorkReportStatusEnum e = fromCode(code);
        return e == null ? code : e.label;
    }
}
