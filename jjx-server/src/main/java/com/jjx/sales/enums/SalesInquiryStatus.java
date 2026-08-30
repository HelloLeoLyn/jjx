package com.jjx.sales.enums;

import com.jjx.common.enums.BizStatusEnum;

import lombok.Getter;

@Getter
public enum SalesInquiryStatus implements BizStatusEnum {
    DRAFT(0, "草稿"),
    PENDING(1, "待处理"),
    SENT(2, "已发送"),
    CONVERTED(3, "已转报价"),
    ACCEPTED(4, "已确认"),
    REJECTED(5, "已拒绝"),
    EXPIRED(6, "已过期");

    private final Integer value;
    private final String label;

    SalesInquiryStatus(Integer value, String label) {
        this.value = value;
        this.label = label;
    }

    public static SalesInquiryStatus getByValue(Integer value) {
        if (value == null) return null;
        for (SalesInquiryStatus s : values()) {
            if (s.value.equals(value)) return s;
        }
        return null;
    }

}
