package com.jjx.inventory.enums;

/** 入库检验结果的唯一取值口径。 */
public enum InspectionResultEnum {
    PENDING, PASS, FAIL, PARTIAL, REINSPECTION;

    public static boolean isValid(String value) {
        if (value == null || value.isBlank()) return false;
        try {
            valueOf(value.trim().toUpperCase());
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
