package com.jjx.inventory.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjx.inventory.domain.InventoryMaterialSpecAttrDef;
import com.jjx.inventory.dto.update.MaterialSpecValuesDTO;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 物料规格文本组合与校验（dev-20261023-022）。
 * <p>
 * 口径（用户 2026-10-23 采纳）：
 * 1）规格不再让人自由编写，按品类属性选择/填数字，由本类组合成一段文本；
 * 2）组合结果写回 inventory_material.specification，下游（BOM/打印/采购/来料检验）只认这段文本；
 * 3）生成文本之外允许 ≤30 字补充说明，仅用于旧预览（主表补充列已删除），统计只认属性值；
 * 4）尺寸进 size、颜色进 color、厂牌型号进 model，属性里不再重复这三类信息。
 * <p>
 * 本类为纯逻辑，不依赖 Spring，便于单测。
 */
public final class MaterialSpecComposer {

    /** 补充说明上限 */
    public static final int MAX_SUPPLEMENT = 30;
    /** 生成文本总长上限（打印「规格及模数」不宜过长） */
    public static final int MAX_TOTAL = 200;
    /** 单个属性片段长度上限 */
    public static final int MAX_SEGMENT = 40;
    /** 数值上限（value_num 为 DECIMAL(14,4)） */
    private static final BigDecimal MAX_NUMBER = new BigDecimal("10000000");
    /** 补充说明与生成文本之间的分隔符 */
    public static final String SUPPLEMENT_SEPARATOR = "；";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MaterialSpecComposer() {
    }

    /** 解析 select 选项 JSON；损坏的配置按空列表处理，不让主数据页整体报错。 */
    public static List<String> parseOptions(String optionsJson) {
        if (optionsJson == null || optionsJson.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return MAPPER.readValue(optionsJson, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * 按属性定义组合规格文本。
     *
     * @param defs        该品类的属性定义（内部按 sortOrder 排序）
     * @param items       用户提交的属性取值
     * @param supplement  补充说明，可为空
     * @param createMode  true = 新增场景，按 required 校验必填
     * @return 组合后的规格文本（可能为空串）
     * @throws IllegalArgumentException 校验不通过，message 为可展示的中文原因
     */
    public static String compose(List<InventoryMaterialSpecAttrDef> defs,
                                 List<MaterialSpecValuesDTO.Item> items,
                                 String supplement,
                                 boolean createMode) {
        List<InventoryMaterialSpecAttrDef> ordered = new ArrayList<>(defs == null ? List.of() : defs);
        ordered.sort(Comparator.comparingInt(d -> d.getSortOrder() == null ? 0 : d.getSortOrder()));

        Map<String, MaterialSpecValuesDTO.Item> byCode = new HashMap<>();
        if (items != null) {
            for (MaterialSpecValuesDTO.Item item : items) {
                String code = item == null ? null : trimToNull(item.getAttrCode());
                if (code == null) {
                    continue;
                }
                boolean known = ordered.stream().anyMatch(d -> code.equals(d.getAttrCode()));
                if (!known) {
                    throw new IllegalArgumentException("规格属性未定义：" + code + "（品类属性配置可能已变更，请重新打开表单）");
                }
                byCode.put(code, item);
            }
        }

        List<String> segments = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (InventoryMaterialSpecAttrDef def : ordered) {
            MaterialSpecValuesDTO.Item item = byCode.get(def.getAttrCode());
            String segment = describeValue(def, item);
            if (segment == null) {
                if (createMode && def.getRequired() != null && def.getRequired() == 1) {
                    missing.add(def.getAttrName());
                }
                continue;
            }
            if (segment.length() > MAX_SEGMENT) {
                throw new IllegalArgumentException("【" + def.getAttrName() + "】过长，单段不超过 " + MAX_SEGMENT + " 字");
            }
            segments.add(segment);
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("请补全规格属性：" + String.join("、", missing));
        }

        String supplementText = trimToNull(supplement);
        if (supplementText != null && supplementText.length() > MAX_SUPPLEMENT) {
            throw new IllegalArgumentException("规格补充不超过 " + MAX_SUPPLEMENT + " 字（当前 " + supplementText.length() + "）");
        }

        String text = String.join(" ", segments);
        if (supplementText != null) {
            text = text.isEmpty() ? supplementText : text + SUPPLEMENT_SEPARATOR + supplementText;
        }
        if (text.length() > MAX_TOTAL) {
            throw new IllegalArgumentException("规格文本超过 " + MAX_TOTAL + " 字（当前 " + text.length() + "），请精简属性");
        }
        return text;
    }

    /**
     * 单个属性渲染成文本片段；未填返回 null。
     *
     * @throws IllegalArgumentException 取值不合法
     */
    public static String describeValue(InventoryMaterialSpecAttrDef def, MaterialSpecValuesDTO.Item item) {
        if (def == null || item == null) {
            return null;
        }
        String type = def.getInputType() == null ? "" : def.getInputType().trim().toLowerCase();
        String unit = def.getUnit() == null ? "" : def.getUnit().trim();

        if ("select".equals(type)) {
            String value = trimToNull(item.getValue());
            if (value == null) {
                return null;
            }
            List<String> options = parseOptions(def.getOptionsJson());
            for (String option : options) {
                if (option != null && option.trim().equals(value)) {
                    return option.trim();
                }
            }
            throw new IllegalArgumentException("【" + def.getAttrName() + "】取值不在可选项内：" + value);
        }

        if ("number".equals(type)) {
            String raw = trimToNull(stripUnit(item.getValue(), unit));
            if (raw == null) {
                return null;
            }
            return formatNumber(raw, def) + unit;
        }

        if ("range".equals(type)) {
            String low = trimToNull(stripUnit(item.getValue(), unit));
            String high = trimToNull(stripUnit(item.getValue2(), unit));
            if (low == null && high == null) {
                return null;
            }
            if (low == null) {
                return formatNumber(high, def) + unit;
            }
            if (high == null) {
                return formatNumber(low, def) + unit;
            }
            BigDecimal lowValue = parseNumber(low, def);
            BigDecimal highValue = parseNumber(high, def);
            if (highValue.compareTo(lowValue) < 0) {
                throw new IllegalArgumentException("【" + def.getAttrName() + "】上限不能小于下限");
            }
            return formatNumber(low, def) + "-" + formatNumber(high, def) + unit;
        }

        // text 及其他未知类型：原样保留（配置写错时不阻断录入）
        String value = trimToNull(item.getValue());
        return value;
    }

    private static String formatNumber(String raw, InventoryMaterialSpecAttrDef def) {
        return parseNumber(raw, def).stripTrailingZeros().toPlainString();
    }

    private static BigDecimal parseNumber(String raw, InventoryMaterialSpecAttrDef def) {
        BigDecimal value;
        try {
            value = new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("【" + def.getAttrName() + "】请填数字：" + raw);
        }
        if (value.signum() <= 0) {
            throw new IllegalArgumentException("【" + def.getAttrName() + "】必须大于 0");
        }
        if (value.compareTo(MAX_NUMBER) > 0) {
            throw new IllegalArgumentException("【" + def.getAttrName() + "】数值过大");
        }
        if (value.stripTrailingZeros().scale() > 4) {
            throw new IllegalArgumentException("【" + def.getAttrName() + "】最多 4 位小数");
        }
        return value;
    }

    /** 用户常把单位一起打进数字框（0.38mm），统一剥掉再解析。 */
    public static String stripUnit(String value, String unit) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        if (!unit.isEmpty() && text.toLowerCase().endsWith(unit.toLowerCase())) {
            text = text.substring(0, text.length() - unit.length()).trim();
        }
        return text;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }
}
