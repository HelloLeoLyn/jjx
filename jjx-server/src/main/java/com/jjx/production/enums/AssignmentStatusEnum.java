package com.jjx.production.enums;

import lombok.Getter;

/**
 * 工序作业分配状态枚举（WP-B）
 * <p>
 * ACTIVE    ：有效分配（可能部分报工）
 * COMPLETED ：剩余数量为 0（由数量事实派生，不人工点按钮）
 * CANCELLED ：整份取消（无任何有效报工时允许；部分报工后释放走 released_quantity 表达，不整份取消）
 */
@Getter
public enum AssignmentStatusEnum {

    ACTIVE("ACTIVE", "有效"),
    COMPLETED("COMPLETED", "已完成"),
    CANCELLED("CANCELLED", "已取消");

    private final String code;
    private final String label;

    AssignmentStatusEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public static AssignmentStatusEnum fromCode(String code) {
        if (code == null) return null;
        for (AssignmentStatusEnum e : values()) {
            if (e.code.equals(code)) return e;
        }
        return null;
    }

    public static String labelOf(String code) {
        AssignmentStatusEnum e = fromCode(code);
        return e == null ? code : e.label;
    }
}
