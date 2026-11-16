package com.jjx.purchase.domain.enums;

/** 采购方案预检状态；不表示订单已批准、已到货或缺料已消除。 */
public enum PurchasePlanSchemeStatus {
    UNARRANGED,
    NEEDS_CONFIRMATION,
    READY
}
