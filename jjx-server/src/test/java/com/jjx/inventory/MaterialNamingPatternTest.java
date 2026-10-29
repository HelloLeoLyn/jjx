package com.jjx.inventory;

import com.jjx.common.exception.BusinessException;
import com.jjx.inventory.domain.InventoryMaterial;
import com.jjx.inventory.master.MaterialAttributeSchema;
import com.jjx.inventory.master.MaterialNamingPattern;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 命名规则：勾选字段 + 顺序 + 分隔符 + 缺值处理（dev-20261028-005）。 */
class MaterialNamingPatternTest {

    private static final MaterialAttributeSchema SCHEMA = new MaterialAttributeSchema(
            1, List.of("PET"), false, "PET", "FILM", "M",
            List.of(new MaterialAttributeSchema.Field("surface_treatment", "表面处理", "enum", null, false,
                    List.of(new MaterialAttributeSchema.Option("MATTE", "哑面")), null, null, null, null, null,
                    true, true, 1, null, null)),
            null);

    private static InventoryMaterial material() {
        InventoryMaterial material = new InventoryMaterial();
        material.setMaterialCode("RM000001");
        material.setMaterialName("旧名");
        material.setBrand("3M");
        material.setModel("125");
        material.setColor("BLACK");
        material.setMaterialMeta("PET");
        material.setSizeUnit("mm");
        material.setWidthMm(new java.math.BigDecimal("300"));
        material.setLengthMm(new java.math.BigDecimal("305"));
        material.setThicknessMm(new java.math.BigDecimal("0.175"));
        material.setAttributeJson("{\"surface_treatment\":\"MATTE\"}");
        return material;
    }

    @Test
    void 按勾选顺序拼装并跳过缺值() {
        var pattern = new MaterialNamingPattern(
                List.of("brand", "model", "thickness_mm", "width", "length", "surface_treatment", "unit"), " ", "skip");
        assertEquals("3M 125 0.175 300 305 哑面", pattern.compose(material(), SCHEMA));
    }

    @Test
    void 缺值可占位且分隔符取首个字符() {
        var pattern = new MaterialNamingPattern(List.of("brand", "color", "unit"), "-", "placeholder");
        assertEquals("3M-黑-—", pattern.compose(material(), SCHEMA));
    }

    @Test
    void 归一化去重保序并把非法缺值模式降级为跳过() {
        var normalized = new MaterialNamingPattern(List.of("Brand", "brand", "model "), "", "unknown").normalized();
        assertEquals(List.of("brand", "model"), normalized.fields());
        assertEquals(" ", normalized.separator());
        assertEquals("skip", normalized.empty());
        assertNull(new MaterialNamingPattern(List.of(), " ", "skip").compose(material(), SCHEMA));
    }

    @Test
    void 未知字段在模板校验时被拒绝() {
        var pattern = new MaterialNamingPattern(List.of("brand", "not_exists"), " ", "skip");
        assertThrows(BusinessException.class, () -> pattern.validate(SCHEMA));
    }

    @Test
    void 尺寸与克重字段属于允许的公共字段() {
        var pattern = new MaterialNamingPattern(List.of("width", "length", "thickness_mm", "grammage"), " ", "skip");
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> pattern.validate(SCHEMA));
    }

    @Test
    void 模板JSON里可带命名规则且能往返解析() {
        String json = "{\"version\":1,\"meta_options\":[\"PET\"],\"fields\":[],"
                + "\"name_pattern\":{\"fields\":[\"brand\",\"model\"],\"separator\":\"-\",\"empty\":\"skip\"}}";
        var schema = MaterialAttributeSchema.parse(json, new com.fasterxml.jackson.databind.ObjectMapper());
        assertEquals(List.of("brand", "model"), schema.namePattern().fields());
        assertEquals("-", schema.namePattern().separator());
        assertEquals("skip", schema.namePattern().empty());
    }
}
