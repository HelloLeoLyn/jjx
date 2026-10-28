package com.jjx.inventory.master;

import com.jjx.common.exception.BusinessException;
import com.jjx.inventory.domain.InventoryMaterial;

/** 属性保存仅同步模板归属；尺寸由数值字段独立维护。 */
public final class MaterialAttributeColumns {
    private MaterialAttributeColumns() { }

    public static void apply(InventoryMaterial material, Long templateId) {
        if (templateId == null) throw new BusinessException("所选分类未配置属性模板");
        material.setTemplateId(templateId);
    }
}
