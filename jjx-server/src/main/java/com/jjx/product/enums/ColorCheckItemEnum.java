package com.jjx.product.enums;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 规范分色检查表检查项：稳定键 + 展示名。
 * <p>稳定键用于保存结果的索引与历史比对，展示名随参考样张；两者在前后端各定义一份，保持同步。</p>
 * <p>四块：规范、面板菲林分色、线路、刀模治具；共 25 项。</p>
 */
public enum ColorCheckItemEnum {

    // 规范
    SPEC_SOURCE_DOCUMENTS("spec.source_documents", "规范", "资料（客供资料、工程图纸）"),
    SPEC_NAME_LABEL("spec.name_label", "规范", "品名标签"),
    SPEC_MATERIAL_SPEC("spec.material_spec", "规范", "材料规格"),
    SPEC_CUSTOMER_INTERNAL_NOTES("spec.customer_internal_notes", "规范", "客户要求和内部备注"),
    SPEC_PROCESSING_SEQUENCE("spec.processing_sequence", "规范", "加工冲形作业工序"),
    SPEC_PRINT_COLOR_SEQUENCE("spec.print_color_sequence", "规范", "印刷色序（客户要求和备注）"),
    SPEC_ARTWORK_DIE_DIMENSIONS("spec.artwork_die_dimensions", "规范", "产品彩图和刀模尺寸图"),

    // 面板菲林分色
    PANEL_PRINTABLE_CONTENT("panel.printable_content", "面板菲林分色", "内容（可印刷）"),
    PANEL_BLEED("panel.bleed", "面板菲林分色", "出血"),
    PANEL_SURFACE_EFFECT("panel.surface_effect", "面板菲林分色", "表面效果"),
    PANEL_DIRECTION("panel.direction", "面板菲林分色", "方向"),
    PANEL_PITCH("panel.pitch", "面板菲林分色", "跳距"),
    PANEL_FILM_LABEL("panel.film_label", "面板菲林分色", "菲林标签（目数和名称）"),

    // 线路
    CIRCUIT_FUNCTIONAL_ROUTING("circuit.functional_routing", "线路", "功能走线"),
    CIRCUIT_LED("circuit.led", "线路", "LED灯（大小颜色正负极）"),
    CIRCUIT_ANNOTATIONS("circuit.annotations", "线路", "辅助标注"),
    CIRCUIT_DIRECTION("circuit.direction", "线路", "方向"),
    CIRCUIT_UV_JUMPER("circuit.uv_jumper", "线路", "UV跳线点"),

    // 刀模治具
    TOOLING_OUTLINE_DIE("tooling.outline_die", "刀模治具", "外形刀"),
    TOOLING_SPACER_DIE("tooling.spacer_die", "刀模治具", "隔片刀"),
    TOOLING_ADHESIVE_DIE("tooling.adhesive_die", "刀模治具", "背胶刀"),
    TOOLING_EMBOSS_DIE("tooling.emboss_die", "刀模治具", "凹凸模"),
    TOOLING_DIRECTION("tooling.direction", "刀模治具", "方向"),
    TOOLING_SCALE("tooling.scale", "刀模治具", "缩放"),
    TOOLING_OTHER_DIE("tooling.other_die", "刀模治具", "垫片保护膜刀等");

    private final String code;
    private final String group;
    private final String label;

    ColorCheckItemEnum(String code, String group, String label) {
        this.code = code;
        this.group = group;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getGroup() {
        return group;
    }

    public String getLabel() {
        return label;
    }

    private static final Map<String, ColorCheckItemEnum> BY_CODE;
    private static final Map<String, String> LABELS;
    private static final Set<String> CODES;

    static {
        Map<String, ColorCheckItemEnum> byCode = new LinkedHashMap<>();
        Map<String, String> labels = new LinkedHashMap<>();
        Set<String> codes = new LinkedHashSet<>();
        for (ColorCheckItemEnum item : values()) {
            byCode.put(item.code, item);
            labels.put(item.code, item.label);
            codes.add(item.code);
        }
        BY_CODE = Collections.unmodifiableMap(byCode);
        LABELS = Collections.unmodifiableMap(labels);
        CODES = Collections.unmodifiableSet(codes);
    }

    /** 稳定键 → 展示名（保存顺序即声明顺序，用于稳定判等）。 */
    public static Map<String, String> labels() {
        return LABELS;
    }

    /** 允许的稳定键集合。 */
    public static Set<String> codes() {
        return CODES;
    }

    public static ColorCheckItemEnum of(String code) {
        return BY_CODE.get(code);
    }
}
