package com.jjx.inventory.enums;

import lombok.Getter;

/**
 * 物料相关枚举容器
 * 统一管理物料模块的所有枚举
 */
public final class MaterialEnums {

    private MaterialEnums() {
        // 私有构造函数，防止实例化
    }

    /**
     * 物料类型枚举
     */
    public enum Type {
        /** 原材料 */
        RAW("R", "原材料", "danger"),
        /** 半成品 */
        SEMI("S", "半成品", "warning"),
        /** 成品 */
        FINISHED("F", "成品", "success"),
        /** 辅助材料 */
        AUXILIARY("A", "辅助材料", "info");

        private final String value;
        private final String label;
        private final String tagType;

        Type(String value, String label, String tagType) {
            this.value = value;
            this.label = label;
            this.tagType = tagType;
        }

        public String getValue() { return value; }
        public String getLabel() { return label; }
        public String getTagType() { return tagType; }

        public static Type fromValue(String value) {
            for (Type type : values()) {
                if (type.value.equals(value)) {
                    return type;
                }
            }
            return null;
        }
    }

    /**
     * 物料状态枚举
     */
    @Getter
    public enum Status {
        /** 启用 */
        ACTIVE("active", "启用", "success"),
        /** 停用 */
        INACTIVE("inactive", "停用", "danger"),
        /** 废弃 */
        OBSOLETE("obsolete", "废弃", "info");

        private final String value;
        private final String label;
        private final String tagType;

        Status(String value, String label, String tagType) {
            this.value = value;
            this.label = label;
            this.tagType = tagType;
        }

        public static Status fromValue(String value) {
            for (Status status : values()) {
                if (status.value.equals(value)) {
                    return status;
                }
            }
            return null;
        }
    }

    /**
     * 批次管理枚举
     */
    public enum BatchControl {
        /** 启用 */
        ENABLED(1, "启用", "success"),
        /** 禁用 */
        DISABLED(0, "禁用", "info");

        private final Integer value;
        private final String label;
        private final String tagType;

        BatchControl(Integer value, String label, String tagType) {
            this.value = value;
            this.label = label;
            this.tagType = tagType;
        }

        public Integer getValue() { return value; }
        public String getLabel() { return label; }
        public String getTagType() { return tagType; }

        public static BatchControl fromValue(Integer value) {
            for (BatchControl bc : values()) {
                if (bc.value.equals(value)) {
                    return bc;
                }
            }
            return null;
        }
    }
}
