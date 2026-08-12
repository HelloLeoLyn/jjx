package com.jjx.production.enums;

import lombok.Getter;

/**
 * 派工单状态枚举
 * 0待派工 1已派工 2执行中 3已完成 4已退回
 */
@Getter
public enum DispatchStatusEnum {

    PENDING(0, "待派工"),
    ASSIGNED(1, "已派工"),
    EXECUTING(2, "执行中"),
    COMPLETED(3, "已完成"),
    REJECTED(4, "已退回");

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
