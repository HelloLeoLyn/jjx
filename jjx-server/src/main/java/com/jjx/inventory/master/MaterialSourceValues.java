package com.jjx.inventory.master;

import com.jjx.inventory.domain.InventoryMaterial;

/** 保留来源响应结构；原文仅留档于用户Excel，不从库内备注或生成文本回填。 */
public record MaterialSourceValues(String originalName, String originalSpecification) {
    public static MaterialSourceValues from(InventoryMaterial material) {
        return new MaterialSourceValues(null, null);
    }
}
