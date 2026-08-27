package com.jjx.product.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 产品相关枚举容器
 * 统一管理产品模块的所有枚举
 */
public final class ProductEnums {

    private ProductEnums() {
        // 私有构造函数，防止实例化
    }

    /**
     * 通用工具方法
     */
    public static class Utils {

        private Utils() {
            // 私有构造函数
        }

        /**
         * 验证值是否有效
         * @param enumClass 枚举类
         * @param value 要验证的值
         * @return 是否有效
         */
        public static <E extends Enum<E>> boolean isValidValue(Class<E> enumClass, Integer value) {
            try {
                for (E e : enumClass.getEnumConstants()) {
                    if (e instanceof ValueEnum) {
                        if (((ValueEnum) e).getValue().equals(value)) {
                            return true;
                        }
                    }
                }
            } catch (Exception e) {
                return false;
            }
            return false;
        }

        /**
         * 获取所有枚举值
         * @param enumClass 枚举类
         * @return 值列表
         */
        public static <E extends Enum<E>> List<Integer> getAllValues(Class<E> enumClass) {
            return Arrays.stream(enumClass.getEnumConstants())
                    .filter(e -> e instanceof ValueEnum)
                    .map(e -> ((ValueEnum) e).getValue())
                    .collect(Collectors.toList());
        }

        /**
         * 获取所有枚举标签
         * @param enumClass 枚举类
         * @return 标签列表
         */
        public static <E extends Enum<E>> List<String> getAllLabels(Class<E> enumClass) {
            return Arrays.stream(enumClass.getEnumConstants())
                    .filter(e -> e instanceof ValueEnum)
                    .map(e -> ((ValueEnum) e).getLabel())
                    .collect(Collectors.toList());
        }
    }

    /**
     * 值枚举接口
     */
    public interface ValueEnum {
        Integer getValue();
        String getLabel();
        String getTagType();
    }

    /**
     * 产品类型枚举
     */
    @Getter
    public enum Type implements ValueEnum {
        /** 标准产品 */
        STANDARD(1, "标准产品", "primary"),
        /** 定制产品 */
        CUSTOM(2, "定制产品", "success");

        private final Integer value;
        private final String label;
        private final String tagType;

        Type(Integer value, String label, String tagType) {
            this.value = value;
            this.label = label;
            this.tagType = tagType;
        }

        public static Type fromValue(Integer value) {
            for (Type type : values()) {
                if (type.value.equals(value)) {
                    return type;
                }
            }
            return STANDARD;
        }
    }

    /**
     * 产品状态枚举
     */
    @Getter
    public enum Status implements ValueEnum {
        /** 开发中 */
        DEVELOPING(1, "开发中", "warning"),
        /** 待审核 */
        PENDING(2, "待审核", "warning"),
        /** 审核中 */
        REVIEWING(3, "审核中", "info"),
        /** 已通过 */
        APPROVED(4, "已通过", "success"),
        /** 已驳回 */
        REJECTED(5, "已驳回", "danger"),
        /** 已发布 */
        RELEASED(6, "已发布", "success"),
        /** 停产 */
        OBSOLETE(7, "停产", "danger"),
        /** 取消 */
        CANCELLED(8, "取消", "danger");

        private final Integer value;
        private final String label;
        private final String tagType;

        Status(Integer value, String label, String tagType) {
            this.value = value;
            this.label = label;
            this.tagType = tagType;
        }

        public static Status fromValue(Integer value) {
            for (Status status : values()) {
                if (status.value.equals(value)) {
                    return status;
                }
            }
            return DEVELOPING;
        }
        public boolean isEditable() {
            return this == DEVELOPING || this == REJECTED;
        }

        public boolean isApprovable() {
            return this == PENDING;
        }
    }


    /**
     * BOM类型枚举
     */
    @Getter
    public enum BomType implements ValueEnum {
        /** 工程BOM */
        ENGINEERING(1, "工程BOM", "primary"),
        /** 制造BOM */
        MANUFACTURING(2, "制造BOM", "success");

        private final Integer value;
        private final String label;
        private final String tagType;

        BomType(Integer value, String label, String tagType) {
            this.value = value;
            this.label = label;
            this.tagType = tagType;
        }

        public static BomType fromValue(Integer value) {
            for (BomType type : values()) {
                if (type.value.equals(value)) {
                    return type;
                }
            }
            return ENGINEERING;
        }
    }

    /**
     * BOM状态枚举
     */
    @Getter
    public enum BomStatus implements ValueEnum {
        /** 草稿 */
        DRAFT(1, "草稿", "info"),
        /** 审核中 */
        REVIEWING(2, "审核中", "warning"),
        /** 已批准 */
        APPROVED(3, "已批准", "success"),
        /** 已驳回 */
        REJECT(4, "已驳回", "danger"),
        /** 已作废 */
        OBSOLETE(5, "已作废", "danger");

        private final Integer value;
        private final String label;
        private final String tagType;

        BomStatus(Integer value, String label, String tagType) {
            this.value = value;
            this.label = label;
            this.tagType = tagType;
        }

        public static BomStatus fromValue(Integer value) {
            for (BomStatus status : values()) {
                if (status.value.equals(value)) {
                    return status;
                }
            }
            return DRAFT;
        }

        public boolean isEditable() {
            return this == DRAFT;
        }
    }

    /**
     * 物料来源类型枚举
     */
    @Getter
    public enum SourceType implements ValueEnum {
        /** 外购 */
        BUY(1, "外购", "primary"),
        /** 自制 */
        MAKE(2, "自制", "success");

        private final Integer value;
        private final String label;
        private final String tagType;

        SourceType(Integer value, String label, String tagType) {
            this.value = value;
            this.label = label;
            this.tagType = tagType;
        }

        public static SourceType fromValue(Integer value) {
            for (SourceType type : values()) {
                if (type.value.equals(value)) {
                    return type;
                }
            }
            return BUY;
        }
    }

    /**
     * BOM层枚举（薄膜开关专用）
     */
    @Getter
    public enum BomLayer implements ValueEnum {
        /** 面板层 */
        OVERLAY(1, "面板层", "primary"),
        /** 上层线路 */
        UPPER_CIRCUIT(2, "上层线路", "success"),
        /** 间隔层 */
        SPACER(3, "间隔层", "warning"),
        /** 下层线路 */
        LOWER_CIRCUIT(4, "下层线路", "info"),
        /** 背胶层 */
        BACK_ADHESIVE(5, "背胶层", "danger");

        private final Integer value;
        private final String label;
        private final String tagType;

        BomLayer(Integer value, String label, String tagType) {
            this.value = value;
            this.label = label;
            this.tagType = tagType;
        }

        public static BomLayer fromValue(Integer value) {
            for (BomLayer layer : values()) {
                if (layer.value.equals(value)) {
                    return layer;
                }
            }
            return OVERLAY;
        }
    }

    /**
     * 工艺路线状态枚举
     */
    @Getter
    public enum RouteStatus implements ValueEnum {
        /** 草稿 */
        DRAFT(1, "草稿", "info"),
        /** 审核中 */
        REVIEWING(2, "审核中", "warning"),
        /** 已批准 */
        APPROVED(3, "已批准", "success"),
        /** 已作废 */
        OBSOLETE(4, "已作废", "danger");

        private final Integer value;
        private final String label;
        private final String tagType;

        RouteStatus(Integer value, String label, String tagType) {
            this.value = value;
            this.label = label;
            this.tagType = tagType;
        }

        public static RouteStatus fromValue(Integer value) {
            for (RouteStatus status : values()) {
                if (status.value.equals(value)) {
                    return status;
                }
            }
            return DRAFT;
        }

        public boolean isEditable() {
            return this == DRAFT;
        }
    }

    /**
     * 工序类型枚举
     */
    @Getter
    public enum StepType implements ValueEnum {
        /** 丝印 */
        SCREEN_PRINTING(1, "丝印", "primary"),
        /** 冲切 */
        DIE_CUTTING(2, "冲切", "success"),
        /** 贴合 */
        LAMINATION(3, "贴合", "warning"),
        /** 测试 */
        TESTING(4, "测试", "info"),
        /** 包装 */
        PACKAGING(5, "包装", "danger");

        private final Integer value;
        private final String label;
        private final String tagType;

        StepType(Integer value, String label, String tagType) {
            this.value = value;
            this.label = label;
            this.tagType = tagType;
        }

        public static StepType fromValue(Integer value) {
            for (StepType type : values()) {
                if (type.value.equals(value)) {
                    return type;
                }
            }
            return SCREEN_PRINTING;
        }
    }

    /**
     * 产品实例生命周期状态枚举
     */
    @Getter
    public enum LifecycleStatus implements ValueEnum {
        /** 设计阶段 */
        DESIGN(1, "设计阶段", "info"),
        /** 客户确认 */
        CUSTOMER_CONFIRM(2, "客户确认", "warning"),
        /** 备料阶段 */
        MATERIAL_PREPARING(3, "备料阶段", "primary"),
        /** 生产阶段 */
        PRODUCTION(4, "生产阶段", "success"),
        /** 质检阶段 */
        QC(5, "质检阶段", "warning"),
        /** 发货阶段 */
        SHIPPED(6, "发货阶段", "info"),
        /** 完成阶段 */
        COMPLETED(7, "完成阶段", "success"),
        /** 暂停 */
        HOLD(8, "暂停", "danger"),
        /** 返工 */
        REWORK(9, "返工", "danger");

        private final Integer value;
        private final String label;
        private final String tagType;

        LifecycleStatus(Integer value, String label, String tagType) {
            this.value = value;
            this.label = label;
            this.tagType = tagType;
        }

        public static LifecycleStatus fromValue(Integer value) {
            for (LifecycleStatus status : values()) {
                if (status.value.equals(value)) {
                    return status;
                }
            }
            return DESIGN;
        }

        public boolean isInProgress() {
            return this == MATERIAL_PREPARING || this == PRODUCTION || this == QC;
        }

        public boolean isCompleted() {
            return this == COMPLETED;
        }
    }

    /**
     * 产品实例状态枚举
     */
    @Getter
    public enum InstanceStatus implements ValueEnum {
        /** 正常 */
        NORMAL(1, "正常", "success"),
        /** 异常 */
        ABNORMAL(2, "异常", "danger"),
        /** 暂停 */
        SUSPENDED(3, "暂停", "warning"),
        /** 完成 */
        COMPLETED(4, "完成", "info");

        private final Integer value;
        private final String label;
        private final String tagType;

        InstanceStatus(Integer value, String label, String tagType) {
            this.value = value;
            this.label = label;
            this.tagType = tagType;
        }

        public static InstanceStatus fromValue(Integer value) {
            for (InstanceStatus status : values()) {
                if (status.value.equals(value)) {
                    return status;
                }
            }
            return NORMAL;
        }
    }

    /**
     * 设计任务状态枚举
     */
    @Getter
    public enum TaskStatus implements ValueEnum {
        /** 待处理 */
        PENDING(1, "待处理", "warning"),
        /** 处理中 */
        PROCESSING(2, "处理中", "info"),
        /** 已完成 */
        DONE(3, "已完成", "success"),
        /** 已驳回 */
        REJECTED(4, "已驳回", "danger");

        private final Integer value;
        private final String label;
        private final String tagType;

        TaskStatus(Integer value, String label, String tagType) {
            this.value = value;
            this.label = label;
            this.tagType = tagType;
        }

        public static TaskStatus fromValue(Integer value) {
            for (TaskStatus status : values()) {
                if (status.value.equals(value)) {
                    return status;
                }
            }
            return PENDING;
        }
    }

    /**
     * 设计任务类型枚举
     */
    @Getter
    public enum TaskType implements ValueEnum {
        /** 设计 */
        DESIGN(1, "设计", "primary"),
        /** 审核 */
        REVIEW(2, "审核", "warning"),
        /** 修改 */
        MODIFY(3, "修改", "info");

        private final Integer value;
        private final String label;
        private final String tagType;

        TaskType(Integer value, String label, String tagType) {
            this.value = value;
            this.label = label;
            this.tagType = tagType;
        }

        public static TaskType fromValue(Integer value) {
            for (TaskType type : values()) {
                if (type.value.equals(value)) {
                    return type;
                }
            }
            return DESIGN;
        }
    }

    /**
     * 菲林状态枚举
     */
    @Getter
    public enum FilmStatus implements ValueEnum {
        /** 草稿 */
        DRAFT(1, "草稿", "info"),
        /** 审核中 */
        REVIEWING(2, "审核中", "warning"),
        /** 已批准 */
        APPROVED(3, "已批准", "success"),
        /** 已作废 */
        OBSOLETE(4, "已作废", "danger");

        private final Integer value;
        private final String label;
        private final String tagType;

        FilmStatus(Integer value, String label, String tagType) {
            this.value = value;
            this.label = label;
            this.tagType = tagType;
        }

        public static FilmStatus fromValue(Integer value) {
            for (FilmStatus status : values()) {
                if (status.value.equals(value)) {
                    return status;
                }
            }
            return DRAFT;
        }
    }

    /**
     * 菲林类型枚举
     */
    @Getter
    public enum FilmType implements ValueEnum {
        /** 面板菲林 */
        OVERLAY(1, "面板菲林", "primary"),
        /** 上层线路菲林 */
        UPPER_CIRCUIT(2, "上层线路菲林", "success"),
        /** 间隔菲林 */
        SPACER(3, "间隔菲林", "warning"),
        /** 下层线路菲林 */
        LOWER_CIRCUIT(4, "下层线路菲林", "info");

        private final Integer value;
        private final String label;
        private final String tagType;

        FilmType(Integer value, String label, String tagType) {
            this.value = value;
            this.label = label;
            this.tagType = tagType;
        }

        public static FilmType fromValue(Integer value) {
            for (FilmType type : values()) {
                if (type.value.equals(value)) {
                    return type;
                }
            }
            return OVERLAY;
        }
    }

    /**
     * 配置选项类型枚举
     */
    @Getter
    public enum ConfigOptionType implements ValueEnum {
        /** 材料 */
        MATERIAL(1, "材料", "primary"),
        /** 颜色 */
        COLOR(2, "颜色", "success"),
        /** 电路 */
        CIRCUIT(3, "电路", "warning"),
        /** 尺寸 */
        SIZE(4, "尺寸", "info");

        private final Integer value;
        private final String label;
        private final String tagType;

        ConfigOptionType(Integer value, String label, String tagType) {
            this.value = value;
            this.label = label;
            this.tagType = tagType;
        }

        public static ConfigOptionType fromValue(Integer value) {
            for (ConfigOptionType type : values()) {
                if (type.value.equals(value)) {
                    return type;
                }
            }
            return MATERIAL;
        }
    }

    /**
     * 配置模型状态枚举
     */
    @Getter
    public enum ConfigModelStatus implements ValueEnum {
        /** 激活 */
        ACTIVE(1, "激活", "success"),
        /** 未激活 */
        INACTIVE(0, "未激活", "danger");

        private final Integer value;
        private final String label;
        private final String tagType;

        ConfigModelStatus(Integer value, String label, String tagType) {
            this.value = value;
            this.label = label;
            this.tagType = tagType;
        }

        public static ConfigModelStatus fromValue(Integer value) {
            for (ConfigModelStatus status : values()) {
                if (status.value.equals(value)) {
                    return status;
                }
            }
            return INACTIVE;
        }

        public boolean isActive() {
            return this == ACTIVE;
        }
    }

    /**
     * 产品分类状态枚举
     */
    @Getter
    public enum CategoryStatus implements ValueEnum {
        /** 正常 */
        NORMAL(0, "正常", "success"),
        /** 停用 */
        DISABLE(1, "停用", "danger");

        private final Integer value;
        private final String label;
        private final String tagType;

        CategoryStatus(Integer value, String label, String tagType) {
            this.value = value;
            this.label = label;
            this.tagType = tagType;
        }

        public static CategoryStatus fromValue(Integer value) {
            for (CategoryStatus status : values()) {
                if (status.value.equals(value)) {
                    return status;
                }
            }
            return NORMAL;
        }

        public boolean isNormal() {
            return this == NORMAL;
        }

        public boolean isDisable() {
            return this == DISABLE;
        }
    }
}
