package com.jjx.production.enums;

import com.jjx.common.enums.BizStatusEnum;

import lombok.Getter;

/**
 * 执行状态枚举
 */
@Getter
public enum ExecutionStatusEnum implements BizStatusEnum {

    /**
     * 待执行 - 等待开始执行
     */
    PENDING(0, "待执行", "等待开始执行"),

    /**
     * 准备中 - 准备工作进行中
     */
    PREPARING(1, "准备中", "准备工作进行中"),

    /**
     * 执行中 - 正在执行
     */
    EXECUTING(2, "执行中", "正在执行"),

    /**
     * 已暂停 - 执行暂停
     */
    PAUSED(3, "已暂停", "执行暂停"),

    /**
     * 已完成 - 执行完成
     */
    COMPLETED(4, "已完成", "执行完成"),

    /**
     * 已跳过 - 跳过执行
     */
    SKIPPED(5, "已跳过", "跳过执行"),

    /**
     * 已取消 - 执行取消
     */
    CANCELLED(6, "已取消", "执行取消"),

    /**
     * 已超期 - 超过计划完成时间
     */
    OVERDUE(7, "已超期", "超过计划完成时间"),

    /**
     * 异常中 - 执行异常
     */
    ABNORMAL(8, "异常中", "执行异常"),

    /**
     * 待确认 - 等待确认结果
     */
    PENDING_CONFIRMATION(9, "待确认", "等待确认结果");

    /**
     * 编码
     */
    private final Integer value;

    /**
     * 名称
     */
    private final String label;

    /**
     * 描述
     */
    private final String description;

    ExecutionStatusEnum(Integer value, String label, String description) {
        this.value = value;
        this.label = label;
        this.description = description;
    }

    /**
     * 根据编码获取枚举
     */
    public static ExecutionStatusEnum getByValue(Integer value) {
        for (ExecutionStatusEnum status : values()) {
            if (status.getValue().equals(value)) {
                return status;
            }
        }
        return null;
    }

    /**
     * 根据名称获取枚举
     */
    public static ExecutionStatusEnum getByName(String label) {
        for (ExecutionStatusEnum status : values()) {
            if (status.getLabel().equals(label)) {
                return status;
            }
        }
        return null;
    }

    /**
     * 检查编码是否存在
     */
    public static boolean containsCode(Integer value) {
        return getByValue(value) != null;
    }

    /**
     * 检查名称是否存在
     */
    public static boolean containsName(String label) {
        return getByName(label) != null;
    }

    /**
     * 获取所有编码
     */
    public static Integer[] getAllValues() {
        ExecutionStatusEnum[] values = values();
        Integer[] valueArray = new Integer[values.length];
        for (int i = 0; i < values.length; i++) {
            valueArray[i] = values[i].getValue();
        }
        return valueArray;
    }

    /**
     * 获取所有名称
     */
    public static String[] getAllNames() {
        ExecutionStatusEnum[] values = values();
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            names[i] = values[i].getLabel();
        }
        return names;
    }

    /**
     * 是否为待执行状态
     */
    public boolean isPending() {
        return this == PENDING || this == PREPARING;
    }

    /**
     * 是否为执行中状态
     */
    public boolean isExecuting() {
        return this == EXECUTING;
    }

    /**
     * 是否为暂停状态
     */
    public boolean isPaused() {
        return this == PAUSED;
    }

    /**
     * 是否为完成状态
     */
    public boolean isCompleted() {
        return this == COMPLETED || this == SKIPPED;
    }

    /**
     * 是否为结束状态
     */
    public boolean isFinalStatus() {
        return this == COMPLETED || this == SKIPPED || this == CANCELLED;
    }

    /**
     * 是否为异常状态
     */
    public boolean isAbnormal() {
        return this == ABNORMAL || this == OVERDUE;
    }

    /**
     * 是否可以开始执行
     */
    public boolean canStart() {
        return this == PENDING || this == PREPARING || this == PAUSED;
    }

    /**
     * 是否可以暂停
     */
    public boolean canPause() {
        return this == EXECUTING;
    }

    /**
     * 是否可以完成
     */
    public boolean canComplete() {
        return this == EXECUTING || this == PAUSED;
    }

    /**
     * 是否可以取消
     */
    public boolean canCancel() {
        return !isFinalStatus() && this != CANCELLED;
    }

    /**
     * 获取状态流转顺序（数值越小越靠前）
     */
    public int getFlowOrder() {
        switch (this) {
            case PENDING:
                return 1;
            case PREPARING:
                return 2;
            case EXECUTING:
                return 3;
            case PAUSED:
                return 4;
            case COMPLETED:
                return 5;
            case SKIPPED:
                return 6;
            case CANCELLED:
                return 7;
            case OVERDUE:
                return 8;
            case ABNORMAL:
                return 9;
            case PENDING_CONFIRMATION:
                return 10;
            default:
                return 99;
        }
    }

    /**
     * 获取下一个可能的状态
     */
    public ExecutionStatusEnum[] getNextPossibleStatuses() {
        switch (this) {
            case PENDING:
                return new ExecutionStatusEnum[]{PREPARING, EXECUTING, SKIPPED, CANCELLED};
            case PREPARING:
                return new ExecutionStatusEnum[]{EXECUTING, CANCELLED};
            case EXECUTING:
                return new ExecutionStatusEnum[]{PAUSED, COMPLETED, ABNORMAL, OVERDUE};
            case PAUSED:
                return new ExecutionStatusEnum[]{EXECUTING, CANCELLED};
            case COMPLETED:
                return new ExecutionStatusEnum[]{PENDING_CONFIRMATION};
            case SKIPPED:
                return new ExecutionStatusEnum[]{};
            case CANCELLED:
                return new ExecutionStatusEnum[]{};
            case OVERDUE:
                return new ExecutionStatusEnum[]{EXECUTING, CANCELLED};
            case ABNORMAL:
                return new ExecutionStatusEnum[]{EXECUTING, CANCELLED};
            case PENDING_CONFIRMATION:
                return new ExecutionStatusEnum[]{COMPLETED, ABNORMAL};
            default:
                return new ExecutionStatusEnum[]{};
        }
    }

    /**
     * 获取显示文本（编码 + 名称）
     */
    public String getDisplayText() {
        return value + " - " + label;
    }

    /**
     * 获取详细描述
     */
    public String getDetailedDescription() {
        return label + "：" + description;
    }

    /**
     * 获取状态颜色（用于前端显示）
     */
    public String getColor() {
        switch (this) {
            case PENDING:
                return "gray";
            case PREPARING:
                return "yellow";
            case EXECUTING:
                return "green";
            case PAUSED:
                return "orange";
            case COMPLETED:
                return "blue";
            case SKIPPED:
                return "cyan";
            case CANCELLED:
                return "red";
            case OVERDUE:
                return "error";
            case ABNORMAL:
                return "warning";
            case PENDING_CONFIRMATION:
                return "purple";
            default:
                return "default";
        }
    }

    /**
     * 获取状态图标（用于前端显示）
     */
    public String getIcon() {
        switch (this) {
            case PENDING:
                return "clock";
            case PREPARING:
                return "setting";
            case EXECUTING:
                return "play";
            case PAUSED:
                return "pause";
            case COMPLETED:
                return "check-circle";
            case SKIPPED:
                return "forward";
            case CANCELLED:
                return "ban";
            case OVERDUE:
                return "warning";
            case ABNORMAL:
                return "exclamation-circle";
            case PENDING_CONFIRMATION:
                return "question-circle";
            default:
                return "file";
        }
    }
}
