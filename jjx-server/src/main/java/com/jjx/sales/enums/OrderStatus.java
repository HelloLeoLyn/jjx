package com.jjx.sales.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;

/**
 * 订单状态枚举
 * 定义订单的完整生命周期状态
 */
@Getter
public enum OrderStatus {

    /**
     * 草稿状态 - 订单创建后，未提交审核
     */
    DRAFT(1, "草稿", "订单创建后，未提交审核"),

    /**
     * 待审核 - 已提交审核，等待审核人审核
     */
    PENDING_REVIEW(2, "待审核", "已提交审核，等待审核人审核"),

    /**
     * 审核中 - 审核人正在审核
     */
    REVIEWING(3, "审核中", "审核人正在审核"),

    /**
     * 已审核 - 审核通过
     */
    APPROVED(4, "已审核", "审核通过"),

    /**
     * 已驳回 - 审核未通过
     */
    REJECTED(5, "已驳回", "审核未通过"),

    /**
     * 待客户确认 - 审核通过后，等待客户确认
     */
    PENDING_CUSTOMER_CONFIRM(6, "待客户确认", "审核通过后，等待客户确认"),

    /**
     * 已确认 - 客户已确认订单
     */
    CONFIRMED(7, "已确认", "客户已确认订单"),

    /**
     * 生产中 - 订单已进入生产流程
     */
    IN_PRODUCTION(8, "生产中", "订单已进入生产流程"),

    /**
     * 已发货 - 产品已发货
     */
    SHIPPED(9, "已发货", "产品已发货"),

    /**
     * 已完成 - 订单已完成（客户收货确认）
     */
    COMPLETED(10, "已完成", "订单已完成（客户收货确认）"),

    /**
     * 已取消 - 订单已取消
     */
    CANCELLED(11, "已取消", "订单已取消"),

    /**
     * 已过期 - 订单已过期（如报价过期）
     */
    EXPIRED(12, "已过期", "订单已过期（如报价过期）");

    private final Integer code;
    private final String name;
    private final String description;

    OrderStatus(Integer code, String name, String description) {
        this.code = code;
        this.name = name;
        this.description = description;
    }

    /**
     * 根据状态码获取状态枚举
     */
    public static OrderStatus getByCode(Integer code) {
        return Arrays.stream(values())
                .filter(status -> status.getCode().equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("无效的订单状态码: " + code));
    }

    /**
     * 获取状态名称
     */
    public static String getNameByCode(Integer code) {
        return getByCode(code).getName();
    }

    /**
     * 检查状态码是否有效
     */
    public static boolean isValidCode(Integer code) {
        return Arrays.stream(values())
                .anyMatch(status -> status.getCode().equals(code));
    }

    /**
     * 获取可提交审核的状态列表
     */
    public static List<OrderStatus> getSubmittableStatuses() {
        return Arrays.asList(DRAFT, REJECTED);
    }

    /**
     * 获取可审核的状态列表
     */
    public static List<OrderStatus> getReviewableStatuses() {
        return Arrays.asList(PENDING_REVIEW, REVIEWING);
    }

    /**
     * 获取可客户确认的状态列表
     */
    public static List<OrderStatus> getConfirmableStatuses() {
        return Arrays.asList(PENDING_CUSTOMER_CONFIRM);
    }

    /**
     * 获取可取消的状态列表
     */
    public static List<OrderStatus> getCancellableStatuses() {
        return Arrays.asList(DRAFT, PENDING_REVIEW, REVIEWING, APPROVED, PENDING_CUSTOMER_CONFIRM);
    }

    /**
     * 检查是否可提交审核
     */
    public boolean isSubmittable() {
        return getSubmittableStatuses().contains(this);
    }

    /**
     * 检查是否可审核
     */
    public boolean isReviewable() {
        return getReviewableStatuses().contains(this);
    }

    /**
     * 检查是否可客户确认
     */
    public boolean isConfirmable() {
        return getConfirmableStatuses().contains(this);
    }

    /**
     * 检查是否可取消
     */
    public boolean isCancellable() {
        return getCancellableStatuses().contains(this);
    }

    /**
     * 检查是否为终态
     */
    public boolean isFinal() {
        return Arrays.asList(COMPLETED, CANCELLED, EXPIRED).contains(this);
    }

    /**
     * 检查是否为进行中状态
     */
    public boolean isInProgress() {
        return Arrays.asList(PENDING_REVIEW, REVIEWING, APPROVED, PENDING_CUSTOMER_CONFIRM,
                           CONFIRMED, IN_PRODUCTION, SHIPPED).contains(this);
    }

    /**
     * 获取下一个可能的状态列表
     */
    public List<OrderStatus> getNextPossibleStatuses() {
        switch (this) {
            case DRAFT:
                return Arrays.asList(PENDING_REVIEW, CANCELLED);
            case PENDING_REVIEW:
                return Arrays.asList(REVIEWING, CANCELLED);
            case REVIEWING:
                return Arrays.asList(APPROVED, REJECTED, CANCELLED);
            case APPROVED:
                return Arrays.asList(PENDING_CUSTOMER_CONFIRM, CANCELLED);
            case REJECTED:
                return Arrays.asList(DRAFT, CANCELLED);
            case PENDING_CUSTOMER_CONFIRM:
                return Arrays.asList(CONFIRMED, CANCELLED, EXPIRED);
            case CONFIRMED:
                return Arrays.asList(IN_PRODUCTION, CANCELLED);
            case IN_PRODUCTION:
                return Arrays.asList(SHIPPED, CANCELLED);
            case SHIPPED:
                return Arrays.asList(COMPLETED, CANCELLED);
            case COMPLETED:
            case CANCELLED:
            case EXPIRED:
                return Arrays.asList(); // 终态没有下一个状态
            default:
                throw new IllegalStateException("未知的订单状态: " + this);
        }
    }

    /**
     * 检查状态转换是否合法
     */
    public boolean canTransitionTo(OrderStatus targetStatus) {
        return getNextPossibleStatuses().contains(targetStatus);
    }

    /**
     * 获取所有状态列表
     */
    public static List<OrderStatus> getAllStatuses() {
        return Arrays.asList(values());
    }

    /**
     * 获取状态转换描述
     */
    public String getTransitionDescription(OrderStatus targetStatus) {
        if (!canTransitionTo(targetStatus)) {
            return "无法从" + getName() + "转换到" + targetStatus.getName();
        }

        switch (this) {
            case DRAFT:
                if (targetStatus == PENDING_REVIEW) {
                    return "提交审核";
                }
                if (targetStatus == CANCELLED) {
                    return "取消订单";
                }
            case PENDING_REVIEW:
                if (targetStatus == REVIEWING) {
                    return "开始审核";
                }
                if (targetStatus == CANCELLED) {
                    return "取消订单";
                }
            case REVIEWING:
                if (targetStatus == APPROVED) {
                    return "审核通过";
                }
                if (targetStatus == REJECTED) {
                    return "审核驳回";
                }
                if (targetStatus == CANCELLED) {
                    return "取消订单";
                }
            case APPROVED:
                if (targetStatus == PENDING_CUSTOMER_CONFIRM) {
                    return "发送给客户确认";
                }
                if (targetStatus == CANCELLED) {
                    return "取消订单";
                }
            case REJECTED:
                if (targetStatus == DRAFT) {
                    return "重新编辑";
                }
                if (targetStatus == CANCELLED) {
                    return "取消订单";
                }
            case PENDING_CUSTOMER_CONFIRM:
                if (targetStatus == CONFIRMED) {
                    return "客户确认";
                }
                if (targetStatus == CANCELLED) {
                    return "取消订单";
                }
                if (targetStatus == EXPIRED) {
                    return "订单过期";
                }
            case CONFIRMED:
                if (targetStatus == IN_PRODUCTION) {
                    return "开始生产";
                }
                if (targetStatus == CANCELLED) {
                    return "取消订单";
                }
            case IN_PRODUCTION:
                if (targetStatus == SHIPPED) {
                    return "发货";
                }
                if (targetStatus == CANCELLED) {
                    return "取消生产";
                }
            case SHIPPED:
                if (targetStatus == COMPLETED) {
                    return "完成订单";
                }
                if (targetStatus == CANCELLED) {
                    return "取消订单";
                }
            default:
                return "状态转换";
        }
    }
}
