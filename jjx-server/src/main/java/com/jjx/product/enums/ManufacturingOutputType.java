package com.jjx.product.enums;

/**
 * 制造产出身份类型（dev-20261111-004）。
 * 只表达 BOM/路线/作业规范的产出一侧是产品还是物料；不复用 BomType(1/2)，
 * 也不复用 engineering_bom.bom_type 的 engineering/manufacturing 字符串。
 */
public enum ManufacturingOutputType {
    PRODUCT,
    MATERIAL;

    /** 历史数据没有 output_type 时按 PRODUCT 解释，兼容既有产品 BOM/路线/规范。 */
    public static ManufacturingOutputType normalize(String value) {
        if (value == null || value.isBlank()) {
            return PRODUCT;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("制造产出类型非法（应为 PRODUCT/MATERIAL）：" + value);
        }
    }

    public static boolean isMaterial(String value) {
        return normalize(value) == MATERIAL;
    }
}
