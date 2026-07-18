package com.jjx.common.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 产品单位枚举
 */
@Getter
public enum UnitEnum {
    
    // ==================== 数量单位 ====================
    PCS("PCS", "个", "quantity", 1d, "piece"),
    SET("SET", "套", "quantity", 1d, "set"),
    BOX("BOX", "盒", "quantity", 1d, "box"),
    CARTON("CTN", "箱", "quantity", 1d, "carton"),
    
    // ==================== 长度单位 ====================
    MM("MM", "毫米", "length", 0.001, "millimeter"),
    CM("CM", "厘米", "length", 0.01, "centimeter"),
    M("M", "米", "length", 1d, "meter"),
    INCH("INCH", "英寸", "length", 0.0254, "inch"),
    FT("FT", "英尺", "length", 0.3048, "foot"),
    
    // ==================== 面积单位 ====================
    SQM("SQM", "平方米", "area", 1d, "square_meter"),
    SQCM("SQCM", "平方厘米", "area", 0.0001, "square_centimeter"),
    SQMM("SQMM", "平方毫米", "area", 0.000001, "square_millimeter"),
    
    // ==================== 重量单位 ====================
    KG("KG", "千克", "weight", 1d, "kilogram"),
    G("G", "克", "weight", 0.001, "gram"),
    TON("TON", "吨", "weight", 1000d, "ton"),
    LB("LB", "磅", "weight", 0.4536, "pound"),
    
    // ==================== 体积单位 ====================
    L("L", "升", "volume", 1d, "liter"),
    ML("ML", "毫升", "volume", 0.001, "milliliter"),
    CBM("CBM", "立方米", "volume", 1d, "cubic_meter"),
    
    // ==================== 时间单位 ====================
    DAY("DAY", "天", "time", 1d, "day"),
    HOUR("HOUR", "小时", "time", 1.0/24.0, "hour"),
    
    // ==================== 其他单位 ====================
    ROLL("ROLL", "卷", "other", 1d, "roll"),
    SHEET("SHEET", "张", "other", 1d, "sheet"),
    PAIR("PAIR", "双", "other", 1d, "pair");

    private final String code;
    private final String name;
    private final String category;
    private final Double baseConversion;
    private final String englishName;

    UnitEnum(String code, String name, String category, Double baseConversion, String englishName) {
        this.code = code;
        this.name = name;
        this.category = category;
        this.baseConversion = baseConversion;
        this.englishName = englishName;
    }

    private static final Map<String, UnitEnum> CODE_MAP =
        Arrays.stream(values()).collect(Collectors.toMap(UnitEnum::getCode, e -> e));

    public static UnitEnum fromCode(String code) {
        UnitEnum unit = CODE_MAP.get(code);
        if (unit == null) {
            throw new IllegalArgumentException("无效的单位编码: " + code);
        }
        return unit;
    }

    public static String getNameByCode(String code) {
        UnitEnum unit = fromCode(code);
        return unit != null ? unit.getName() : code;
    }

    public static boolean isValidCode(String code) {
        return CODE_MAP.containsKey(code);
    }

    /**
     * 根据类别获取单位列表
     */
    public static List<UnitEnum> getByCategory(String category) {
        return Arrays.stream(values())
                .filter(u -> u.getCategory().equals(category))
                .collect(Collectors.toList());
    }

    /**
     * 获取所有数量单位
     */
    public static List<UnitEnum> getQuantityUnits() {
        return getByCategory("quantity");
    }

    /**
     * 获取所有长度单位
     */
    public static List<UnitEnum> getLengthUnits() {
        return getByCategory("length");
    }

    /**
     * 获取所有面积单位
     */
    public static List<UnitEnum> getAreaUnits() {
        return getByCategory("area");
    }

    /**
     * 获取所有重量单位
     */
    public static List<UnitEnum> getWeightUnits() {
        return getByCategory("weight");
    }

    /**
     * 获取所有体积单位
     */
    public static List<UnitEnum> getVolumeUnits() {
        return getByCategory("volume");
    }
}