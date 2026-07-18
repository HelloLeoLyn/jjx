package com.jjx.sales.enums;

import lombok.Getter;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 订单状态枚举
 */
@Getter
public enum OrderStatusEnum {

    DRAFT(1, "草稿", "订单创建后，未提交审核", true, false),
    PENDING_REVIEW(2, "待审核", "已提交审核，等待审核人审核", false, false),
    REVIEWING(3, "审核中", "审核人正在审核", false, false),
    APPROVED(4, "已审核", "审核通过", false, false),
    REJECTED(5, "已驳回", "审核未通过", true, true),
    CONFIRMED(6, "已确认", "客户已确认订单", false, false),
    IN_PRODUCTION(7, "生产中", "订单已进入生产流程", false, false),
    SHIPPED(8, "已发货", "产品已发货", false, false),
    COMPLETED(9, "已完成", "订单已完成", false, true),
    CANCELLED(10, "已取消", "订单已取消", false, true);

    private final Integer code;
    private final String name;
    private final String description;
    private final boolean editable;
    private final boolean terminal;

    OrderStatusEnum(Integer code, String name, String description, boolean editable, boolean terminal) {
        this.code = code;
        this.name = name;
        this.description = description;
        this.editable = editable;
        this.terminal = terminal;
    }

    // 状态转换规则
    private static final Map<OrderStatusEnum, Set<OrderStatusEnum>> TRANSITIONS = new EnumMap<>(OrderStatusEnum.class);

    static {
        TRANSITIONS.put(DRAFT, EnumSet.of(PENDING_REVIEW, CANCELLED));
        TRANSITIONS.put(PENDING_REVIEW, EnumSet.of(REVIEWING, CANCELLED));
        TRANSITIONS.put(REVIEWING, EnumSet.of(APPROVED, REJECTED, CANCELLED));
        TRANSITIONS.put(APPROVED, EnumSet.of(CONFIRMED, CANCELLED));
        TRANSITIONS.put(REJECTED, EnumSet.of(DRAFT, CANCELLED));
        TRANSITIONS.put(CONFIRMED, EnumSet.of(IN_PRODUCTION, CANCELLED));
        TRANSITIONS.put(IN_PRODUCTION, EnumSet.of(SHIPPED, CANCELLED));
        TRANSITIONS.put(SHIPPED, EnumSet.of(COMPLETED, CANCELLED));
        TRANSITIONS.put(COMPLETED, EnumSet.noneOf(OrderStatusEnum.class));
        TRANSITIONS.put(CANCELLED, EnumSet.noneOf(OrderStatusEnum.class));
    }

    private static final Map<Integer, OrderStatusEnum> CODE_MAP =
            Arrays.stream(values()).collect(Collectors.toMap(OrderStatusEnum::getCode, s -> s));

    public static OrderStatusEnum getByCode(Integer code) {
        OrderStatusEnum status = CODE_MAP.get(code);
        if (status == null) {
            throw new IllegalArgumentException("无效的订单状态码: " + code);
        }
        return status;
    }

    public static Optional<OrderStatusEnum> getByCodeSafe(Integer code) {
        return Optional.ofNullable(CODE_MAP.get(code));
    }

    public boolean canTransitionTo(OrderStatusEnum target) {
        return TRANSITIONS.getOrDefault(this, Collections.emptySet()).contains(target);
    }

    public Set<OrderStatusEnum> getNextStatuses() {
        return TRANSITIONS.getOrDefault(this, Collections.emptySet());
    }

    public boolean isSubmittable() {
        return this == DRAFT || this == REJECTED;
    }

    public boolean isReviewable() {
        return this == PENDING_REVIEW || this == REVIEWING;
    }
}