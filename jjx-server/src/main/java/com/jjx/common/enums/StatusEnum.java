package com.jjx.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 通用状态枚举
 */
@Getter
@AllArgsConstructor
public enum StatusEnum {

    /** 正常 */
    NORMAL(1, "正常"),
    /** 停用 */
    DISABLE(0, "停用"),
    /** 删除 */
    DELETED(2, "删除");

    private final Integer code;
    private final String info;

    /**
     * 根据code获取枚举
     */
    public static StatusEnum valueOfCode(Integer code) {
        for (StatusEnum status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        return null;
    }

    /**
     * 判断code是否有效
     */
    public static boolean isValid(Integer code) {
        return valueOfCode(code) != null;
    }
}
