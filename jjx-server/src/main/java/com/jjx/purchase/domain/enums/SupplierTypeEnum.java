package com.jjx.purchase.domain.enums;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

/**
 * 供应商类型枚举（主要供货类别，单选；明细走系统标签）
 * <p>dev-20260912-003：参考物料类型 inventory_material_type 重构，替代原 M（物料）/E（设备）/O（其他）。</p>
 */
@Getter
public enum SupplierTypeEnum {

    /**
     * 原材料
     */
    MATERIAL("R", "原材料"),

    /**
     * 辅助材料
     */
    AUXILIARY("A", "辅助材料"),

    /**
     * 油墨
     */
    INK("I", "油墨"),

    /**
     * 成品（外购）
     */
    FINISHED("F", "成品"),

    /**
     * 设备
     */
    EQUIPMENT("E", "设备"),

    /**
     * 其他
     */
    OTHER("O", "其他");

    private final String code;
    private final String label;

    SupplierTypeEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /**
     * 根据code获取枚举
     */
    public static SupplierTypeEnum getByCode(String code) {
        if (StringUtils.isBlank(code)) {
            return null;
        }
        String normalized = code.trim().toUpperCase();
        for (SupplierTypeEnum type : values()) {
            if (type.code.equals(normalized)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 判断code是否有效
     */
    public static boolean isValid(String code) {
        return getByCode(code) != null;
    }

    /**
     * 判断是否为原材料
     */
    public boolean isMaterial() {
        return this == MATERIAL;
    }

    /**
     * 判断是否为设备
     */
    public boolean isEquipment() {
        return this == EQUIPMENT;
    }

    /**
     * 判断是否为其他
     */
    public boolean isOther() {
        return this == OTHER;
    }

    /**
     * 按「供货品类」归类到供应商类型（dev-20260912-003）
     * <p>来源：供应商导入表「供应商类型」列实际是「大类*明细」的供货品类（如 油墨*丝印油墨）。</p>
     * <p>规则：命中关键词即归类；未命中归「其他 O」。辅助材料类（电子元件/化学品/制版耗材等）
     * 按 Leo 2026-09-12 决定暂统一归「原材料」，操作员后续可自改。</p>
     *
     * @param goodsCategory 供货品类，可含「大类*明细」，只取 * 前的大类参与匹配
     * @return 供应商类型编码，永不为 null（兜底 O）
     */
    public static String classifyByGoodsCategory(String goodsCategory) {
        if (StringUtils.isBlank(goodsCategory)) {
            return OTHER.code;
        }
        String head = goodsCategory.trim();
        int star = head.indexOf('*');
        if (star > 0) {
            head = head.substring(0, star).trim();
        }
        if (containsAny(head, "油墨", "滴胶")) {
            return INK.code;
        }
        if (containsAny(head, "设备", "机械", "仪器", "工装")) {
            return EQUIPMENT.code;
        }
        if (containsAny(head, "成品", "外购")) {
            return FINISHED.code;
        }
        if (containsAny(head, "塑料", "橡胶", "纸制品", "离型", "铝板", "元件", "器件", "填料",
                "化学", "丝印", "网版", "菲林", "刮胶", "洗网水", "弹片", "PIN", "FPC", "印制电路板")) {
            return MATERIAL.code;
        }
        return OTHER.code;
    }

    private static boolean containsAny(String text, String... keywords) {
        if (StringUtils.isBlank(text)) {
            return false;
        }
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @deprecated 使用 { @link #getLabel() }
     */
    @Deprecated
    public String getDescription() {
        return label;
    }
}
