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
