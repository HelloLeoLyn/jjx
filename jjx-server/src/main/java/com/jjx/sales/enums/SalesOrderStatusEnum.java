package com.jjx.sales.enums;

import com.jjx.common.enums.BizStatusEnum;

import lombok.Getter;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 订单状态枚举
 */
@Getter
public enum SalesOrderStatusEnum implements BizStatusEnum {

    DRAFT(1, "草稿", "订单创建后，未提交审核", true, false),
    PENDING_REVIEW(2, "待审核", "已提交审核，等待审核人审核", false, false),
    REVIEWING(3, "审核中", "审核人正在审核", false, false),
    APPROVED(4, "已审核", "审核通过", false, false),
    REJECTED(5, "已驳回", "审核未通过", true, false),
    CONFIRMED(6, "已确认", "客户已确认订单", false, false),
    IN_PRODUCTION(7, "生产中", "订单已进入生产流程", false, false),
    SHIPPED(8, "已发货", "产品已发货", false, false),
    COMPLETED(9, "已完成", "订单已完成", false, true),
    CANCELLED(10, "已取消", "订单已取消", false, true);

    private final Integer value;
    private final String label;
    private final String description;
    private final boolean editable;
    private final boolean terminal;

    SalesOrderStatusEnum(Integer value, String label, String description, boolean editable, boolean terminal) {
        this.value = value;
        this.label = label;
        this.description = description;
        this.editable = editable;
        this.terminal = terminal;
    }

    // 状态转换规则
    private static final Map<SalesOrderStatusEnum, Set<SalesOrderStatusEnum>> TRANSITIONS = new EnumMap<>(SalesOrderStatusEnum.class);

    static {
        TRANSITIONS.put(DRAFT, EnumSet.of(PENDING_REVIEW, CANCELLED));
        TRANSITIONS.put(PENDING_REVIEW, EnumSet.of(REVIEWING, CANCELLED));
        TRANSITIONS.put(REVIEWING, EnumSet.of(APPROVED, REJECTED, CANCELLED));
        TRANSITIONS.put(APPROVED, EnumSet.of(CONFIRMED, IN_PRODUCTION, CANCELLED));
        TRANSITIONS.put(REJECTED, EnumSet.of(DRAFT, CANCELLED));
        TRANSITIONS.put(CONFIRMED, EnumSet.of(IN_PRODUCTION, CANCELLED));
        TRANSITIONS.put(IN_PRODUCTION, EnumSet.of(SHIPPED, CANCELLED));
        TRANSITIONS.put(SHIPPED, EnumSet.of(COMPLETED, CANCELLED));
        TRANSITIONS.put(COMPLETED, EnumSet.noneOf(SalesOrderStatusEnum.class));
        TRANSITIONS.put(CANCELLED, EnumSet.noneOf(SalesOrderStatusEnum.class));
    }

    private static final Map<Integer, SalesOrderStatusEnum> VALUE_MAP =
            Arrays.stream(values()).collect(Collectors.toMap(SalesOrderStatusEnum::getValue, s -> s));

    public static SalesOrderStatusEnum getByValue(Integer value) {
        SalesOrderStatusEnum status = VALUE_MAP.get(value);
        if (status == null) {
            throw new IllegalArgumentException("无效的订单状态码: " + value);
        }
        return status;
    }

    public static Optional<SalesOrderStatusEnum> getByValueSafe(Integer value) {
        return Optional.ofNullable(VALUE_MAP.get(value));
    }

    public boolean canTransitionTo(SalesOrderStatusEnum target) {
        return TRANSITIONS.getOrDefault(this, Collections.emptySet()).contains(target);
    }

    public Set<SalesOrderStatusEnum> getNextStatuses() {
        return TRANSITIONS.getOrDefault(this, Collections.emptySet());
    }

    public boolean isSubmittable() {
        return this == DRAFT || this == REJECTED;
    }

    public boolean isReviewable() {
        return this == PENDING_REVIEW || this == REVIEWING;
    }

    /**
     * 是否可客户确认（已审核状态可确认）
     */
    public boolean isConfirmable() {
        return this == APPROVED;
    }

    /**
     * 是否可取消（状态机中可流转到已取消的状态）
     */
    public boolean isCancellable() {
        return TRANSITIONS.getOrDefault(this, Collections.emptySet()).contains(CANCELLED);
    }

    /**
     * 是否为终态
     */
    public boolean isFinal() {
        return this == COMPLETED || this == CANCELLED;
    }

    /**
     * 是否为进行中状态（非草稿、非终态）
     */
    public boolean isInProgress() {
        return this != DRAFT && !isFinal();
    }
}