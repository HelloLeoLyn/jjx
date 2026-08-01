package com.jjx.sales.enums;

import lombok.Getter;

/**
 * 报价单状态枚举
 * 与数据库 sales_quotation.quotation_status 数字编码一致
 */
@Getter
public enum QuotationStatus {
    DRAFT(0, "草稿"),
    SENT(1, "已发送"),
    ACCEPTED(2, "已确认"),
    REJECTED(3, "已拒绝"),
    EXPIRED(4, "已过期"),
    PENDING_REVIEW(5, "待审核"),
    APPROVED(6, "已审核"),
    MODIFYING(8, "改单"),
    COMPLETED(9, "已完成");

    private final Integer code;
    private final String name;

    QuotationStatus(Integer code, String name) {
        this.code = code;
        this.name = name;
    }

    public static QuotationStatus getByCode(Integer code) {
        if (code == null) return null;
        for (QuotationStatus s : values()) {
            if (s.code.equals(code)) return s;
        }
        return null;
    }
}
