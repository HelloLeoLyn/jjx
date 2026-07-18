package com.jjx.sales.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 生产状态枚举
 */
@Getter
@AllArgsConstructor
public enum ProdStatusEnum {
    NONE(1, "无生产"),
    PARTIAL_PRODUCING(2, "部分生产中"),
    FULL_PRODUCING(3, "全部生产中"),
    COMPLETED(4, "生产完成");

    private final Integer code;
    private final String desc;

    public static ProdStatusEnum getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (ProdStatusEnum value : values()) {
            if (value.getCode().equals(code)) {
                return value;
            }
        }
        return null;
    }
}
