package com.jjx.production.enums;

import lombok.Getter;

/**
 * 生产报工状态枚举（P2：报工事实状态）
 * SUBMITTED：已提交（计入汇总，不可编辑）
 * CANCELLED：已撤销（不计入汇总，原事实字段保留，禁止物理删除）
 * 不做 DRAFT/APPROVED/REJECTED（当前业务无报工审批流程，P3 Quality 接入后再评估）。
 */
@Getter
public enum WorkReportStatusEnum {

    SUBMITTED("SUBMITTED", "已提交"),
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
