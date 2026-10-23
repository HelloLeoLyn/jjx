package com.jjx.inventory;

import com.jjx.inventory.domain.InventoryMaterialSpecAttrDef;
import com.jjx.inventory.dto.update.MaterialSpecValuesDTO;
import com.jjx.inventory.util.MaterialSpecComposer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 物料规格属性组合与校验（dev-20261023-022）。
 * 覆盖：正常组合、单位重复、区间、补充说明、必填、非法取值、未定义属性、长度上限、排序。
 */
class MaterialSpecComposeTest {

    private static InventoryMaterialSpecAttrDef def(String code, String name, String type, String unit,
                                                    String options, int required, int sort) {
        InventoryMaterialSpecAttrDef d = new InventoryMaterialSpecAttrDef();
        d.setCategoryCode("RM-FILM");
        d.setAttrCode(code);
        d.setAttrName(name);
        d.setInputType(type);
        d.setUnit(unit);
        d.setOptionsJson(options);
        d.setRequired(required);
        d.setSortOrder(sort);
        d.setStatus(1);
        return d;
    }

    private static MaterialSpecValuesDTO.Item item(String code, String value) {
        return item(code, value, null);
    }

    private static MaterialSpecValuesDTO.Item item(String code, String value, String value2) {
        MaterialSpecValuesDTO.Item i = new MaterialSpecValuesDTO.Item();
        i.setAttrCode(code);
        i.setValue(value);
        i.setValue2(value2);
        return i;
    }

    private static final List<InventoryMaterialSpecAttrDef> FILM_DEFS = List.of(
            def("thickness", "厚度", "number", "mm", null, 1, 10),
            def("surface", "表面处理", "select", null,
                    "[\"光面\",\"雾面\",\"幼砂\",\"中砂\",\"加硬\",\"幼砂加硬\"]", 0, 20),
            def("adhesive_side", "胶面", "select", null, "[\"无胶\",\"单面\",\"双面\"]", 0, 30)
    );

    @Test
    void composesFilmSpecification() {
        String text = MaterialSpecComposer.compose(FILM_DEFS,
                List.of(item("thickness", "0.38"), item("surface", "幼砂加硬")), null, true);
        assertEquals("0.38mm 幼砂加硬", text);
    }

    @Test
    void keepsDeclarationOrderAndStripsRepeatedUnit() {
        // 提交顺序颠倒 + 数值里带单位，结果仍按属性排序、单位只出现一次
        String text = MaterialSpecComposer.compose(FILM_DEFS,
                List.of(item("adhesive_side", "双面"), item("thickness", "0.25MM")), "", true);
        assertEquals("0.25mm 双面", text);
    }

    @Test
    void composesRangeForPaper() {
        List<InventoryMaterialSpecAttrDef> paperDefs = List.of(
                def("gram", "克重", "number", "g", null, 1, 10),
                def("release_force", "离型力", "range", "g", null, 0, 20));
        String text = MaterialSpecComposer.compose(paperDefs,
                List.of(item("gram", "120"), item("release_force", "10", "15")), null, true);
        assertEquals("120g 10-15g", text);
    }

    @Test
    void appendsSupplementAfterSeparator() {
        String text = MaterialSpecComposer.compose(FILM_DEFS,
                List.of(item("thickness", "0.38"), item("surface", "幼砂")), "客供", true);
        assertEquals("0.38mm 幼砂；客供", text);
    }

    @Test
    void supplementAloneIsAllowed() {
        String text = MaterialSpecComposer.compose(FILM_DEFS, List.of(), "按图加工", false);
        assertEquals("按图加工", text);
    }

    @Test
    void createModeRequiresMandatoryAttribute() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> MaterialSpecComposer.compose(FILM_DEFS, List.of(item("surface", "光面")), null, true));
        assertTrue(error.getMessage().contains("厚度"), error.getMessage());
    }

    @Test
    void updateModeDoesNotBlockLegacyRowsWithoutAttributes() {
        // 反例保护：历史物料没有属性值，编辑其它字段时不能被规格必填拦住
        assertEquals("", MaterialSpecComposer.compose(FILM_DEFS, List.of(), null, false));
    }

    @Test
    void rejectsOptionOutsideDefinition() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> MaterialSpecComposer.compose(FILM_DEFS,
                        List.of(item("thickness", "0.38"), item("surface", "磨砂")), null, true));
        assertTrue(error.getMessage().contains("表面处理"), error.getMessage());
    }

    @Test
    void rejectsReversedRangeAndNonPositiveNumber() {
        List<InventoryMaterialSpecAttrDef> paperDefs = List.of(
                def("release_force", "离型力", "range", "g", null, 0, 10));
        assertThrows(IllegalArgumentException.class, () -> MaterialSpecComposer.compose(paperDefs,
                List.of(item("release_force", "15", "10")), null, false));
        assertThrows(IllegalArgumentException.class, () -> MaterialSpecComposer.compose(FILM_DEFS,
                List.of(item("thickness", "0")), null, false));
        assertThrows(IllegalArgumentException.class, () -> MaterialSpecComposer.compose(FILM_DEFS,
                List.of(item("thickness", "厚")), null, false));
    }

    @Test
    void rejectsUnknownAttributeCode() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> MaterialSpecComposer.compose(FILM_DEFS, List.of(item("color", "白色")), null, false));
        assertTrue(error.getMessage().contains("未定义"), error.getMessage());
    }

    @Test
    void enforcesLengthLimits() {
        assertThrows(IllegalArgumentException.class, () -> MaterialSpecComposer.compose(FILM_DEFS,
                List.of(item("thickness", "0.38")), "超".repeat(MaterialSpecComposer.MAX_SUPPLEMENT + 1), false));
        List<InventoryMaterialSpecAttrDef> textDefs = List.of(def("note", "说明", "text", null, null, 0, 10));
        String tooLong = "长".repeat(MaterialSpecComposer.MAX_SEGMENT + 1);
        assertThrows(IllegalArgumentException.class,
                () -> MaterialSpecComposer.compose(textDefs, List.of(item("note", tooLong)), null, false));
    }

    @Test
    void blankValuesAreIgnored() {
        String text = MaterialSpecComposer.compose(FILM_DEFS,
                List.of(item("thickness", "   "), item("adhesive_side", "")), null, false);
        assertEquals("", text);
        assertFalse(text.contains("null"));
    }

    @Test
    void parsesOptionJsonSafely() {
        assertEquals(List.of("光面", "雾面"), MaterialSpecComposer.parseOptions("[\"光面\",\"雾面\"]"));
        assertTrue(MaterialSpecComposer.parseOptions("光面,雾面").isEmpty());
        assertTrue(MaterialSpecComposer.parseOptions(null).isEmpty());
    }
}
