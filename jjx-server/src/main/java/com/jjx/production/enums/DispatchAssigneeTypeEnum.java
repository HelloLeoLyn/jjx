package com.jjx.production.enums;

import lombok.Getter;

/**
 * 派工责任主体类型枚举（P1 第一版仅支持 USER）
 * 组织信息（orgId/orgName/orgPath）只作为责任人当时所属组织快照保存，
 * ORG/TEAM/WORKSHOP 等组织节点类型 P1 不实现（不能成为 ACTIVE owner）。
 */
@Getter
public enum DispatchAssigneeTypeEnum {

    USER("USER", "用户");

    private final String code;
    private final String label;

    DispatchAssigneeTypeEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public static DispatchAssigneeTypeEnum fromCode(String code) {
        if (code == null) return null;
        for (DispatchAssigneeTypeEnum e : values()) {
            if (e.code.equals(code)) return e;
        }
        return null;
    }

    /** 未知/历史值原样返回（兼容展示，不抛异常） */
    public static String labelOf(String code) {
        DispatchAssigneeTypeEnum e = fromCode(code);
        return e == null ? code : e.label;
    }
}
