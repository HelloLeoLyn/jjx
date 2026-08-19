package com.jjx.production.enums;

import lombok.Getter;

/**
 * 派工流水动作枚举（P1-C：Node 化动作 + 旧动作兼容）
 * 新动作：ASSIGN/DELEGATE/REASSIGN/RETURN（Node 化）
 * 旧动作：REJECT/START/COMPLETE（保持兼容，非 Node 化）
 */
@Getter
public enum DispatchLogActionEnum {

    ASSIGN("ASSIGN", "指派"),
    DELEGATE("DELEGATE", "下派"),
    REASSIGN("REASSIGN", "改派"),
    RETURN("RETURN", "退回"),
    REJECT("REJECT", "整单退回"),
    START("START", "开始"),
    COMPLETE("COMPLETE", "完成");

    private final String code;
    private final String label;

    DispatchLogActionEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public static DispatchLogActionEnum fromCode(String code) {
        if (code == null) return null;
        for (DispatchLogActionEnum e : values()) {
            if (e.code.equals(code)) return e;
        }
        return null;
    }

    public static String labelOf(String code) {
        DispatchLogActionEnum e = fromCode(code);
        return e == null ? code : e.label;
    }
}
