package com.jjx.common.util;

import com.jjx.common.exception.BusinessException;

import java.util.Map;
import java.util.Set;

/**
 * 单位口径唯一工具（dev-20261113-003）。
 *
 * <p>真源：{@code sys_dict_item(dict_code='unit')} 的 item_value：
 * M / ㎡ / PCS / KG / SHEET / SET / PIN / ROLL。本类只做别名归一与字典校验，
 * 不承载换算系数；换算仍由各业务既有逻辑处理。</p>
 *
 * <p>其他位置禁止再自写单位别名表；新增别名必须加在这里并补测试。</p>
 */
public final class UnitCodes {

    public static final String M = "M";
    public static final String SQM = "㎡";
    public static final String PCS = "PCS";
    public static final String KG = "KG";
    public static final String ROLL = "ROLL";
    public static final String SHEET = "SHEET";
    public static final String SET = "SET";
    public static final String PIN = "PIN";

    /** 字典当前启用的单位 code 集合。 */
    private static final Set<String> DICT_UNITS = Set.of(M, SQM, PCS, KG, ROLL, SHEET, SET, PIN);

    /** 常见别名 → 字典 code；键按原样与去空白后比较（区分大小写，㎡/m² 本身就是符号）。 */
    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("㎡", SQM),
            Map.entry("m²", SQM),
            Map.entry("m2", SQM),
            Map.entry("M2", SQM),
            Map.entry("平方米", SQM),
            Map.entry("平方", SQM),
            Map.entry("SQ", SQM),
            Map.entry("m", M),
            Map.entry("M", M),
            Map.entry("米", M),
            Map.entry("PCS", PCS),
            Map.entry("件", PCS),
            Map.entry("个", PCS),
            Map.entry("pcs", PCS),
            Map.entry("KG", KG),
            Map.entry("公斤", KG),
            Map.entry("千克", KG),
            Map.entry("kg", KG),
            Map.entry("ROLL", ROLL),
            Map.entry("卷", ROLL),
            Map.entry("roll", ROLL),
            Map.entry("SHEET", SHEET),
            Map.entry("张", SHEET),
            Map.entry("sheet", SHEET),
            Map.entry("SET", SET),
            Map.entry("套", SET),
            Map.entry("set", SET),
            Map.entry("PIN", PIN),
            Map.entry("针", PIN),
            Map.entry("pin", PIN)
    );

    private UnitCodes() {
    }

    /**
     * 归一为字典 code：㎡/m²/m2/M2/平方米/平方 → ㎡；米/m → M；公斤 → KG；件 → PCS；卷 → ROLL 等。
     * 未知值去空白后原样返回，由调用方决定是否拒绝（本方法不猜）。
     */
    public static String normalize(String unit) {
        if (unit == null) {
            return null;
        }
        String value = unit.trim();
        if (value.isEmpty()) {
            return value;
        }
        String mapped = ALIASES.get(value);
        if (mapped != null) {
            return mapped;
        }
        String upper = value.toUpperCase();
        if (upper.length() > 1 && ALIASES.containsKey(upper)) {
            return ALIASES.get(upper);
        }
        return value;
    }

    /** 是否属于字典单位（先归一，再判断；未知值不算）。 */
    public static boolean isDictUnit(String unit) {
        String normalized = normalize(unit);
        return normalized != null && DICT_UNITS.contains(normalized);
    }

    /**
     * 写入校验：单位必须属于字典单位，否则抛中文业务异常。
     * 用于物料、BOM、采购明细、库存单据等写 unit 的入口。
     */
    public static String requireDictUnit(String unit, String fieldLabel) {
        if (unit == null || unit.isBlank()) {
            throw new BusinessException(fieldLabel + "不能为空");
        }
        String normalized = normalize(unit);
        if (!DICT_UNITS.contains(normalized)) {
            throw new BusinessException(fieldLabel + "[" + unit.trim() + "]不是有效单位，请选择："
                    + String.join("、", DICT_UNITS));
        }
        return normalized;
    }

    /** 供测试与其他模块读取的字典单位集合（只读）。 */
    public static Set<String> dictUnits() {
        return DICT_UNITS;
    }
}
