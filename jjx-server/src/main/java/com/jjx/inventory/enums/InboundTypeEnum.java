package com.jjx.inventory.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 入库类型枚举
 */
@Getter
@AllArgsConstructor
public enum InboundTypeEnum {

    PURCHASE("purchase", "采购入库"),
    PRODUCTION("production", "生产入库"),
    RETURN("return", "退货入库"),
    TRANSFER("transfer", "调拨入库"),
    ADJUST("adjust", "盘盈入库"),
    IQC_RELEASE("iqc_release", "让步接收入库"),
    IQC_REWORK("iqc_rework", "返工复检合格入库"),
    FQC_CONCESSION("fqc_concession", "成品让步特采入库");

    private final String code;
    private final String label;

    public static InboundTypeEnum getByCode(String code) {
        for (InboundTypeEnum value : values()) {
            if (value.getCode().equals(code)) {
                return value;
            }
        }
        return null;
    }

}
