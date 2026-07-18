package com.jjx.purchase.domain.enums;

import lombok.Getter;

/**
 * 供应商类型枚举
 */
@Getter
public enum SupplierTypeEnum {

    /**
     * 原材料供应商
     */
    MATERIAL("M", "原材料供应商"),

    /**
     * 设备供应商
     */
    EQUIPMENT("E", "设备供应商"),

    /**
     * 其他供应商
     */
    OTHER("O", "其他供应商");

    private final String code;
    private final String description;

    SupplierTypeEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据code获取枚举
     */
    public static SupplierTypeEnum getByCode(String code) {
        for (SupplierTypeEnum type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 判断是否为原材料供应商
     */
    public boolean isMaterial() {
        return this == MATERIAL;
    }

    /**
     * 判断是否为设备供应商
     */
    public boolean isEquipment() {
        return this == EQUIPMENT;
    }

    /**
     * 判断是否为其他供应商
     */
    public boolean isOther() {
        return this == OTHER;
    }

    /**
     * 判断code是否有效
     */
    public static boolean isValid(String code) {
        return getByCode(code) != null;
    }
}
