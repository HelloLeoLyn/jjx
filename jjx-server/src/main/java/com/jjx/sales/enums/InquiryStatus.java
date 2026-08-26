package com.jjx.sales.enums;

import lombok.Getter;

@Getter
public enum InquiryStatus {
    DRAFT(0, "草稿"),
    PENDING(1, "待处理"),
    SENT(2, "已发送"),
    CONVERTED(3, "已转报价"),
    ACCEPTED(4, "已确认"),
    REJECTED(5, "已拒绝"),
    EXPIRED(6, "已过期");

    private final Integer code;
    private final String name;

    InquiryStatus(Integer code, String name) {
        this.code = code;
        this.name = name;
    }

    public static InquiryStatus getByCode(Integer code) {
        if (code == null) return null;
        for (InquiryStatus s : values()) {
            if (s.code.equals(code)) return s;
        }
        return null;
    }

}
