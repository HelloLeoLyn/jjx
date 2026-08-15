package com.jjx.production.enums;

import lombok.Getter;

/**
 * 派工单状态枚举（2026-08-13 多级执行人链：0待派工 1已派班组 2已派工 3执行中 4已完成 5已退回）
 * 已派班组=主管已定班组+一级执行人，链未完整；已派工=执行人链完整可开工
 */
@Getter
public enum DispatchStatusEnum {

    PENDING(0, "待派工"),
    TEAM_ASSIGNED(1, "已派班组"),
    ASSIGNED(2, "已派工"),
    EXECUTING(3, "执行中"),
    COMPLETED(4, "已完成"),
    REJECTED(5, "已退回");

    private final Integer code;
    private final String label;

    DispatchStatusEnum(Integer code, String label) {
        this.code = code;
        this.label = label;
    }

    public static DispatchStatusEnum fromCode(Integer code) {
        if (code == null) return null;
        for (DispatchStatusEnum e : values()) {
            if (e.code.equals(code)) return e;
        }
        return null;
    }

    public static String labelOf(Integer code) {
        DispatchStatusEnum e = fromCode(code);
        return e == null ? null : e.label;
    }
}
