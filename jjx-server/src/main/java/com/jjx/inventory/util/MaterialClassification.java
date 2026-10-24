package com.jjx.inventory.util;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jjx.common.exception.BusinessException;
import com.jjx.inventory.domain.InventoryMaterial;
import java.util.Map;

/** 分类路径是物料业务类型与新编码前缀的唯一来源；未知分类不作默认归类。 */
public final class MaterialClassification {
    private MaterialClassification() { }

    public static String materialTypeOf(Map<String, Object> category) {
        String root = rootOf(category);
        if (root == null) return null;
        return switch (root) { case "RAW" -> "R"; case "SEMI" -> "S"; case "FINISHED" -> "F"; default -> null; };
    }

    public static String rootNameOf(Map<String, Object> category) {
        String root = rootOf(category);
        if (root == null) return null;
        return switch (root) { case "RAW" -> "原材料"; case "SEMI" -> "半成品"; case "FINISHED" -> "成品"; default -> null; };
    }

    public static String codePrefixOf(Map<String, Object> category) {
        String path = pathOf(category);
        if (path == null) throw new BusinessException("物料未归类，无法生成编码，请先选择分类");
        String[] nodes = path.split("/");
        if ("SEMI".equals(nodes[0])) return "SFG";
        if ("FINISHED".equals(nodes[0])) return "FG";
        if ("RAW".equals(nodes[0]) && nodes.length > 1) {
            return switch (nodes[1]) {
                case "RAW_BASE" -> "RM";
                case "RAW_AUX" -> "AUX";
                case "RAW_INK" -> "INK";
                default -> throw new BusinessException("分类无有效编码族，请检查二级分类");
            };
        }
        throw new BusinessException("请选择具有编码族的分类");
    }

    /** 类型筛选不读主表旧列；NULL分类不会命中任何类型。 */
    public static LambdaQueryWrapper<InventoryMaterial> filterType(LambdaQueryWrapper<InventoryMaterial> query, String type) {
        String root = switch (type == null ? "" : type) {
            case "R" -> "RAW"; case "S" -> "SEMI"; case "F" -> "FINISHED";
            default -> throw new BusinessException("无效的分类根类型：" + type);
        };
        return query.apply("EXISTS (SELECT 1 FROM inventory_material_category mc WHERE mc.category_id = inventory_material.category_id "
                + "AND SUBSTRING_INDEX(mc.category_path, '/', 1) = {0})", root);
    }

    private static String rootOf(Map<String, Object> category) {
        String path = pathOf(category);
        return path == null ? null : path.split("/", 2)[0];
    }

    private static String pathOf(Map<String, Object> category) {
        if (category == null) return null;
        Object value = category.get("category_path");
        if (!(value instanceof String path) || path.isBlank()) return null;
        String root = path.split("/", 2)[0];
        return switch (root) { case "RAW", "SEMI", "FINISHED" -> path; default -> null; };
    }
}
