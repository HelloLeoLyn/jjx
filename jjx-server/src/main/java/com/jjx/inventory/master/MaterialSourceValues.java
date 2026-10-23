package com.jjx.inventory.master;

import com.jjx.inventory.domain.InventoryMaterial;
import java.util.regex.Pattern;

/** 只读取明确来源标记，不从名称猜测品类、型号或物理属性。 */
public record MaterialSourceValues(String originalName, String originalSpecification) {
    public static MaterialSourceValues from(InventoryMaterial material) {
        if (material.getOriginalName() != null) return new MaterialSourceValues(material.getOriginalName(),material.getOriginalSpecification());
        String name=extract(material.getRemark(),"【原名称】(.*?)(?:【原规格】|$)");
        String specification=extract(material.getRemark(),"【原规格】(.*)$");
        return new MaterialSourceValues(name==null?material.getMaterialName():name,
                specification==null?material.getSpecification():specification);
    }
    private static String extract(String text,String expression) {
        var matcher=Pattern.compile(expression,Pattern.DOTALL).matcher(text==null?"":text);
        return matcher.find()?matcher.group(1).trim():null;
    }
}
