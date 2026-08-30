package com.jjx.production.enums;

import com.jjx.common.enums.BizStatusEnum;

import lombok.Getter;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 订单状态枚举
 */
@Getter
public enum ProductionOrderStatusEnum implements BizStatusEnum {

    /**
     * 草稿 - 订单创建但未提交
     */
    DRAFT(0, "草稿", "订单创建但未提交"),

    /**
     * 待审核 - 已提交等待审核
     */
    PENDING_APPROVAL(1, "待审核", "已提交等待审核"),

    /**
     * 已审核 - 审核通过
     */
    APPROVED(2, "已审核", "审核通过"),

    /**
     * 已驳回 - 审核驳回
     */
    REJECTED(3, "已驳回", "审核驳回"),

    /**
     * 已计划 - 已排入生产计划
     */
    PLANNED(4, "已计划", "已排入生产计划"),

    /**
     * 待开始 - 计划时间未到
     */
    PENDING_START(5, "待开始", "计划时间未到"),

    /**
     * 进行中 - 生产执行中
     */
    IN_PROGRESS(6, "进行中", "生产执行中"),

    /**
     * 已暂停 - 生产暂停
     */
    PAUSED(7, "已暂停", "生产暂停"),

    /**
     * 已完成 - 生产完成
     */
    COMPLETED(8, "已完成", "生产完成"),

    /**
     * 已取消 - 订单取消
     */
    CANCELLED(9, "已取消", "订单取消"),

    /**
     * 已关闭 - 订单关闭
     */
    CLOSED(10, "已关闭", "订单关闭"),

    /**
     * 已超期 - 超过计划完成时间
     */
    OVERDUE(11, "已超期", "超过计划完成时间");

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

    private static final Map<Integer, ProductionOrderStatusEnum> VALUE_MAP =
            Arrays.stream(values()).collect(Collectors.toMap(ProductionOrderStatusEnum::getValue, s -> s));

    ProductionOrderStatusEnum(Integer value, String label, String description) {
        this.value = value;
        this.label = label;
        this.description = description;
    }

    /**
     * 根据编码获取枚举
     */
    public static ProductionOrderStatusEnum getByValue(Integer value) {
        return VALUE_MAP.get(value);
    }

    /**
     * 根据名称获取枚举
     */
    public static ProductionOrderStatusEnum getByName(String label) {
        for (ProductionOrderStatusEnum status : values()) {
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
        return VALUE_MAP.containsKey(value);
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
        ProductionOrderStatusEnum[] values = values();
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
        ProductionOrderStatusEnum[] values = values();
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            names[i] = values[i].getLabel();
        }
        return names;
    }

    /**
     * 是否为草稿状态
     */
    public boolean isDraft() {
        return this == DRAFT;
    }

    /**
     * 是否为审核状态
     */
    public boolean isApprovalStatus() {
        return this == PENDING_APPROVAL || this == APPROVED || this == REJECTED;
    }

    /**
     * 是否为计划状态
     */
    public boolean isPlanningStatus() {
        return this == PLANNED || this == PENDING_START;
    }

    /**
     * 是否为执行状态
     */
    public boolean isExecutionStatus() {
        return this == IN_PROGRESS || this == PAUSED || this == COMPLETED;
    }

    /**
     * 是否为结束状态
     */
    public boolean isFinalStatus() {
        return this == COMPLETED || this == CANCELLED || this == CLOSED || this == OVERDUE;
    }

    /**
     * 是否为活动状态（可操作）
     */
    public boolean isActiveStatus() {
        return this == PENDING_START || this == IN_PROGRESS || this == PAUSED;
    }

    /**
     * 是否可以开始执行
     */
    public boolean canStart() {
        return this == PENDING_START || this == PAUSED;
    }

    /**
     * 是否可以暂停
     */
    public boolean canPause() {
        return this == IN_PROGRESS;
    }

    /**
     * 是否可以完成
     */
    public boolean canComplete() {
        return this == IN_PROGRESS || this == PAUSED;
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
        return this.value;
    }

    /**
     * 获取下一个可能的状态
     */
    public ProductionOrderStatusEnum[] getNextPossibleStatuses() {
        switch (this) {
            case DRAFT:
                return new ProductionOrderStatusEnum[]{PENDING_APPROVAL, CANCELLED};
            case PENDING_APPROVAL:
                return new ProductionOrderStatusEnum[]{APPROVED, REJECTED};
            case APPROVED:
                return new ProductionOrderStatusEnum[]{PLANNED, CANCELLED};
            case REJECTED:
                return new ProductionOrderStatusEnum[]{DRAFT, CANCELLED};
            case PLANNED:
                return new ProductionOrderStatusEnum[]{PENDING_START, CANCELLED};
            case PENDING_START:
                return new ProductionOrderStatusEnum[]{IN_PROGRESS, CANCELLED};
            case IN_PROGRESS:
                return new ProductionOrderStatusEnum[]{PAUSED, COMPLETED, OVERDUE};
            case PAUSED:
                return new ProductionOrderStatusEnum[]{IN_PROGRESS, CANCELLED};
            case COMPLETED:
                return new ProductionOrderStatusEnum[]{CLOSED};
            case CANCELLED:
                return new ProductionOrderStatusEnum[]{CLOSED};
            case CLOSED:
                return new ProductionOrderStatusEnum[]{};
            case OVERDUE:
                return new ProductionOrderStatusEnum[]{IN_PROGRESS, CANCELLED};
            default:
                return new ProductionOrderStatusEnum[]{};
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
            case DRAFT:
                return "gray";
            case PENDING_APPROVAL:
                return "orange";
            case APPROVED:
                return "blue";
            case REJECTED:
                return "red";
            case PLANNED:
                return "cyan";
            case PENDING_START:
                return "yellow";
            case IN_PROGRESS:
                return "green";
            case PAUSED:
                return "warning";
            case COMPLETED:
                return "success";
            case CANCELLED:
                return "danger";
            case CLOSED:
                return "default";
            case OVERDUE:
                return "error";
            default:
                return "default";
        }
    }

    /**
     * 获取状态图标（用于前端显示）
     */
    public String getIcon() {
        switch (this) {
            case DRAFT:
                return "edit";
            case PENDING_APPROVAL:
                return "clock";
            case APPROVED:
                return "check";
            case REJECTED:
                return "close";
            case PLANNED:
                return "calendar";
            case PENDING_START:
                return "time";
            case IN_PROGRESS:
                return "play";
            case PAUSED:
                return "pause";
            case COMPLETED:
                return "check-circle";
            case CANCELLED:
                return "ban";
            case CLOSED:
                return "lock";
            case OVERDUE:
                return "warning";
            default:
                return "file";
        }
    }
}
