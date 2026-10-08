package com.jjx.sales.enums;

import com.jjx.common.enums.BizStatusEnum;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 生产状态枚举
 */
@Getter
@AllArgsConstructor
public enum ProdStatusEnum implements BizStatusEnum {
    NONE(1, "无生产"),
    PARTIAL_PRODUCING(2, "部分生产中"),
    FULL_PRODUCING(3, "全部生产中"),
    COMPLETED(4, "生产完成");

    private final Integer value;
    private final String label;

    /** 完工入库/红冲后，按净已产量同步订单生产状态。 */
    public static ProdStatusEnum fromProducedQuantity(Integer produced, Integer required) {
        if (produced == null || produced <= 0) return NONE;
        if (required != null && required > 0 && produced >= required) return COMPLETED;
        return PARTIAL_PRODUCING;
    }

    public static ProdStatusEnum getByValue(Integer value) {
        if (value == null) {
            return null;
        }
        for (ProdStatusEnum status : values()) {
            if (status.getValue().equals(value)) {
                return status;
            }
        }
        return null;
    }
}
