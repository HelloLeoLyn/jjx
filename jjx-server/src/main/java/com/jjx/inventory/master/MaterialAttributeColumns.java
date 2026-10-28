package com.jjx.inventory.master;

import com.jjx.common.exception.BusinessException;
import com.jjx.inventory.domain.InventoryMaterial;
import java.math.BigDecimal;

/** 属性保存时同源派生查询列；生成列由数据库维护。dev-20261024-007 */
public final class MaterialAttributeColumns {
    private MaterialAttributeColumns() { }

    public static void apply(InventoryMaterial material, Long templateId) {
        if (templateId == null) throw new BusinessException("所选分类未配置属性模板");
        // 厚度只认尺寸（size）同源解析，不再接受属性 JSON 里的 thickness_mm 作为第二入口。
        Object thickness = MaterialSizeValue.thickness(material.getSize());
        material.setTemplateId(templateId);
        material.setThickness(text(thickness, 30, "厚度/克重"));
        material.setWidthSize(text(material.getSize(), 50, "尺寸"));
    }

    private static String text(Object value, int max, String label) {
        if (value == null) return null;
        if (!(value instanceof String) && !(value instanceof Number)) throw new BusinessException(label + "必须为文本或数值");
        String text = value instanceof BigDecimal decimal ? decimal.toPlainString() : value.toString().trim();
        if (text.length() > max) throw new BusinessException(label + "不能超过" + max + "字");
        return text.isBlank() ? null : text;
    }
}
