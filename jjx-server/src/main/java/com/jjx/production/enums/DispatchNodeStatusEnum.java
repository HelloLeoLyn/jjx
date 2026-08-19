package com.jjx.production.enums;

import lombok.Getter;

/**
 * 派工责任链节点状态枚举（P1：责任持有实例状态）
 * 表达"本次责任持有实例发生了什么"，与 ExecutionStatus（生产执行）、DispatchStatus（派工单容器）完全分离。
 * ACTIVE：当前责任持有中
 * DELEGATED：已向下委派（监督中）
 * REASSIGNED：已被同级改派（历史不可变，不再激活）
 * RETURNED：已退回上级责任层（不再激活，上级重新持责走新 Node）
 * COMPLETED：责任链最终完成
 * CANCELLED：任务取消
 */
@Getter
public enum DispatchNodeStatusEnum {

    ACTIVE("ACTIVE", "当前责任持有中"),
    DELEGATED("DELEGATED", "已向下委派"),
    REASSIGNED("REASSIGNED", "已被同级改派"),
    RETURNED("RETURNED", "已退回上级责任层"),
    COMPLETED("COMPLETED", "责任链最终完成"),
    CANCELLED("CANCELLED", "任务取消");

    private final String code;
    private final String label;

    DispatchNodeStatusEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public static DispatchNodeStatusEnum fromCode(String code) {
        if (code == null) return null;
        for (DispatchNodeStatusEnum e : values()) {
            if (e.code.equals(code)) return e;
        }
        return null;
    }

    /** 未知/历史值原样返回（兼容展示，不抛异常） */
    public static String labelOf(String code) {
        DispatchNodeStatusEnum e = fromCode(code);
        return e == null ? code : e.label;
    }
}
