package com.jjx.inventory.classify;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.inventory.domain.InventoryMaterial;

public enum MaterialClassStatus {
    UNCLASSIFIED("unclassified"), RULE_UNCONFIRMED("rule_unconfirmed"), CONFIRMED("confirmed");
    private final String code;
    MaterialClassStatus(String code) { this.code = code; }
    public static void filter(LambdaQueryWrapper<InventoryMaterial> query, String code) {
        if (code == null || code.isBlank()) return;
        MaterialClassStatus value = java.util.Arrays.stream(values()).filter(v -> v.code.equals(code))
                .findFirst().orElseThrow(() -> new BusinessException("不支持的归类状态：" + code));
        switch (value) {
            case UNCLASSIFIED -> query.isNull(InventoryMaterial::getCategoryId);
            case RULE_UNCONFIRMED -> query.eq(InventoryMaterial::getClassSource, MaterialClassSource.RULE.name())
                    .eq(InventoryMaterial::getClassConfirmed, false);
            case CONFIRMED -> query.eq(InventoryMaterial::getClassConfirmed, true);
        }
    }
}
