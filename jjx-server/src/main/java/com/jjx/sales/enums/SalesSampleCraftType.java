package com.jjx.sales.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 打样制样类别（dev-20261010-028）
 * 对应 QR-065 样品需求单的两栏制样记录：印刷制样 / 加工冲型制样。
 */
@Getter
@AllArgsConstructor
public enum SalesSampleCraftType {

    PRINT("PRINT", "印刷"),
    PUNCH("PUNCH", "加工冲型");

    private final String code;
    private final String label;

    public static SalesSampleCraftType of(String code) {
        if (code != null) {
            for (SalesSampleCraftType type : values()) {
                if (type.code.equalsIgnoreCase(code)) {
                    return type;
                }
            }
        }
        return null;
    }
}
