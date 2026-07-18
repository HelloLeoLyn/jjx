package com.jjx.production.enums;

import lombok.Getter;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 订单状态枚举
 */
@Getter
public enum OrderStatusEnum {

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
    private final Integer code;

    /**
     * 名称
     */
    private final String name;

    /**
     * 描述
     */
    private final String description;

    private static final Map<Integer, OrderStatusEnum> CODE_MAP =
            Arrays.stream(values()).collect(Collectors.toMap(OrderStatusEnum::getCode, s -> s));

    OrderStatusEnum(Integer code, String name, String description) {
        this.code = code;
        this.name = name;
        this.description = description;
    }

    /**
     * 根据编码获取枚举
     */
    public static OrderStatusEnum getByCode(Integer code) {
        return CODE_MAP.get(code);
    }

    /**
     * 根据名称获取枚举
     */
    public static OrderStatusEnum getByName(String name) {
        for (OrderStatusEnum status : values()) {
            if (status.getName().equals(name)) {
                return status;
            }
        }
        return null;
    }

    /**
     * 检查编码是否存在
     */
    public static boolean containsCode(Integer code) {
        return CODE_MAP.containsKey(code);
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
    public static Integer[] getAllCodes() {
        OrderStatusEnum[] values = values();
        Integer[] codes = new Integer[values.length];
        for (int i = 0; i < values.length; i++) {
            codes[i] = values[i].getCode();
        }
        return codes;
    }

    /**
     * 获取所有名称
     */
    public static String[] getAllNames() {
        OrderStatusEnum[] values = values();
        String[] names = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            names[i] = values[i].getName();
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
        return this.code;
    }

    /**
     * 获取下一个可能的状态
     */
    public OrderStatusEnum[] getNextPossibleStatuses() {
        switch (this) {
            case DRAFT:
                return new OrderStatusEnum[]{PENDING_APPROVAL, CANCELLED};
            case PENDING_APPROVAL:
                return new OrderStatusEnum[]{APPROVED, REJECTED};
            case APPROVED:
                return new OrderStatusEnum[]{PLANNED, CANCELLED};
            case REJECTED:
                return new OrderStatusEnum[]{DRAFT, CANCELLED};
            case PLANNED:
                return new OrderStatusEnum[]{PENDING_START, CANCELLED};
            case PENDING_START:
                return new OrderStatusEnum[]{IN_PROGRESS, CANCELLED};
            case IN_PROGRESS:
                return new OrderStatusEnum[]{PAUSED, COMPLETED, OVERDUE};
            case PAUSED:
                return new OrderStatusEnum[]{IN_PROGRESS, CANCELLED};
            case COMPLETED:
                return new OrderStatusEnum[]{CLOSED};
            case CANCELLED:
                return new OrderStatusEnum[]{CLOSED};
            case CLOSED:
                return new OrderStatusEnum[]{};
            case OVERDUE:
                return new OrderStatusEnum[]{IN_PROGRESS, CANCELLED};
            default:
                return new OrderStatusEnum[]{};
        }
    }

    /**
     * 获取显示文本（编码 + 名称）
     */
    public String getDisplayText() {
        return code + " - " + name;
    }

    /**
     * 获取详细描述
     */
    public String getDetailedDescription() {
        return name + "：" + description;
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
