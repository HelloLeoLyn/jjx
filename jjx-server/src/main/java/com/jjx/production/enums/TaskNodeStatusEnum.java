package com.jjx.production.enums;

import lombok.Getter;

/**
 * 生产任务树节点状态枚举
 * ACTIVE：执行中（可报工/可继续分配下级）
 * COMPLETED：已完成
 * CANCELLED：已取消（历史保留，不计入父节点占用数量）
 */
@Getter
public enum TaskNodeStatusEnum {

    ACTIVE("ACTIVE", "执行中"),
    COMPLETED("COMPLETED", "已完成"),
    CANCELLED("CANCELLED", "已取消");

    private final String code;
    private final String label;

    TaskNodeStatusEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public static TaskNodeStatusEnum fromCode(String code) {
        if (code == null) return null;
        for (TaskNodeStatusEnum e : values()) {
            if (e.code.equals(code)) return e;
        }
        return null;
    }

    /** 未知/历史值原样返回（兼容展示，不抛异常） */
    public static String labelOf(String code) {
        TaskNodeStatusEnum e = fromCode(code);
        return e == null ? code : e.label;
    }
}
