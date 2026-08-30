package com.jjx.production.enums;

import lombok.Getter;

/**
 * 订单类型枚举
 * 用于区分生产计划和生产工单
 */
@Getter
public enum OrderTypeEnum {

    /**
     * 生产计划 - 计划性生产任务
     */
    PLAN("PLAN", "生产计划", "计划性生产任务，用于生产排程"),

    /**
     * 生产工单 - 实际生产任务
     */
    ORDER("ORDER", "生产工单", "实际生产任务，用于生产执行"),

    /**
     * 试产订单 - 新产品试产
     */
    TRIAL("TRIAL", "试产订单", "新产品试产，用于验证工艺"),

    /**
     * 返工订单 - 产品返工
     */
    REWORK("REWORK", "返工订单", "产品返工，用于质量修复"),

    /**
     * 样品订单 - 样品制作
     */
    SAMPLE("SAMPLE", "样品订单", "样品制作，用于客户确认"),

    /**
     * 维修订单 - 设备维修
     */
    REPAIR("REPAIR", "维修订单", "设备维修，用于设备维护"),

    /**
     * 备件订单 - 备件生产
     */
    SPARE("SPARE", "备件订单", "备件生产，用于设备备件"),

    /**
     * 紧急订单 - 紧急生产任务
     */
    URGENT("URGENT", "紧急订单", "紧急生产任务，优先级最高");

    /**
     * 编码
     */
    private final String code;

    /**
     * 名称
     */
    private final String label;

    /**
     * 描述
     */
    private final String description;

    OrderTypeEnum(String code, String name, String description) {
        this.code = code;
        this.label = name;
        this.description = description;
    }

    /**
     * 根据编码获取枚举
     */
    public static OrderTypeEnum getByCode(String code) {
        for (OrderTypeEnum type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 根据名称获取枚举
     */
    public static OrderTypeEnum getByName(String name) {
        for (OrderTypeEnum type : values()) {
            if (type.getName().equals(name)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 检查编码是否存在
     */
    public static boolean containsCode(String code) {
        return getByCode(code) != null;
    }

    /**
     * 检查名称是否存在
     */
    public static boolean containsName(String name) {
        return getByName(name) != null;
    }

    /**
     * 获取所有编码
     */
    public static String[] getAllCodes() {
        OrderTypeEnum[] values = values();
        String[] codes = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            codes[i] = values[i].getCode();
        }
        return codes;
    }

    /**
     * 获取所有名称
     */
    public static String[] getAllNames() {
        OrderTypeEnum[] values = values();
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            names[i] = values[i].getName();
        }
        return names;
    }

    /**
     * 是否为计划类型
     */
    public boolean isPlanType() {
        return this == PLAN;
    }

    /**
     * 是否为工单类型
     */
    public boolean isOrderType() {
        return this == ORDER;
    }

    /**
     * 是否为特殊订单类型
     */
    public boolean isSpecialType() {
        return this == TRIAL || this == REWORK || this == SAMPLE ||
               this == REPAIR || this == SPARE || this == URGENT;
    }

    /**
     * 是否为紧急订单
     */
    public boolean isUrgentType() {
        return this == URGENT;
    }

    /**
     * 是否为质量相关订单
     */
    public boolean isQualityRelated() {
        return this == REWORK || this == SAMPLE;
    }

    /**
     * 是否为设备相关订单
     */
    public boolean isEquipmentRelated() {
        return this == REPAIR || this == SPARE;
    }

    /**
     * 是否需要特殊处理
     */
    public boolean requiresSpecialHandling() {
        return isSpecialType() || isUrgentType();
    }

    /**
     * 获取优先级（数值越小优先级越高）
     */
    public int getPriority() {
        switch (this) {
            case URGENT:
                return 1;
            case ORDER:
                return 2;
            case PLAN:
                return 3;
            case TRIAL:
                return 4;
            case REWORK:
                return 5;
            case SAMPLE:
                return 6;
            case REPAIR:
                return 7;
            case SPARE:
                return 8;
            default:
                return 9;
        }
    }

    /**
     * 获取显示文本（编码 + 名称）
     */
    public String getDisplayText() {
        return code + " - " + label;
    }

    /**
     * 获取详细描述
     */
    public String getDetailedDescription() {
        return label + "：" + description;
    }

    /**
     * 获取订单类型颜色（用于前端显示）
     */
    public String getColor() {
        switch (this) {
            case PLAN:
                return "blue";
            case ORDER:
                return "green";
            case URGENT:
                return "red";
            case TRIAL:
                return "orange";
            case REWORK:
                return "yellow";
            case SAMPLE:
                return "purple";
            case REPAIR:
                return "cyan";
            case SPARE:
                return "gray";
            default:
                return "default";
        }
    }

    /**
     * 获取订单类型图标（用于前端显示）
     */
    public String getIcon() {
        switch (this) {
            case PLAN:
                return "calendar";
            case ORDER:
                return "document";
            case URGENT:
                return "warning";
            case TRIAL:
                return "experiment";
            case REWORK:
                return "refresh";
            case SAMPLE:
                return "box";
            case REPAIR:
                return "tool";
            case SPARE:
                return "component";
            default:
                return "file";
        }
    }

    /**
     * @deprecated 使用 { #getLabel()}
     */
    @Deprecated
    public String getName() {
        return label;
    }
}
