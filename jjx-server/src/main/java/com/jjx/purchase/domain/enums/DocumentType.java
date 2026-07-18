package com.jjx.purchase.domain.enums;

import lombok.Getter;

/**
 * 票据类型枚举
 */
@Getter
public enum DocumentType {

    /**
     * 发票
     */
    INVOICE("invoice", "发票"),

    /**
     * 收据
     */
    RECEIPT("receipt", "收据"),

    /**
     * 合同
     */
    CONTRACT("contract", "合同"),

    /**
     * 报价单
     */
    QUOTATION("quotation", "报价单"),

    /**
     * 送货单
     */
    DELIVERY_NOTE("delivery_note", "送货单"),

    /**
     * 其他
     */
    OTHER("other", "其他");

    private final String code;
    private final String description;

    DocumentType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据code获取枚举
     */
    public static DocumentType getByCode(String code) {
        for (DocumentType type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        return null;
    }
}
