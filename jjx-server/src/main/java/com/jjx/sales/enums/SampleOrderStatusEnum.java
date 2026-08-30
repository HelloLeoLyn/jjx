package com.jjx.sales.enums;

import com.jjx.common.enums.BizStatusEnum;

import lombok.Getter;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 样品订单状态枚举
 * 薄膜开关行业样品单独立状态机
 * 
 * 状态流转：
 * CREATED(1) → PENDING_REVIEW(2) → ENGINEERING(3) → SAMPLE_READY(4)
 * → SAMPLE_SENT(5) → CONFIRMED(6) → TRANSFERRED(7) → CLOSED(8)
 *                ↖ REJECTED(9) 退回后回到 CREATED 或 ENGINEERING 重新打样
 * 任意状态 → CANCELLED(10)
 */
@Getter
public enum SampleOrderStatusEnum implements BizStatusEnum {

    CREATED(1, "样品需求已创建", "样品单已创建，待提交审核", false, false),
    REQUEST(2, "待打样", "提交打样申请，等待工程人员接单", false, false),
    ENGINEERING(3, "工程打样中", "审核通过，工程部门正在打样", false, false),
    SAMPLE_READY(4, "样品待送样", "样品已完成，待发送给客户", false, false),
    SAMPLE_SENT(5, "已送样待确认", "样品已发送客户，等待客户确认", false, false),
    CONFIRMED(6, "样品确认", "客户已确认样品OK", false, false),
    TRANSFERRED(7, "已转量产", "样品确认后已转量产", false, true),
    CLOSED(8, "已关闭", "样品已关闭", false, true),
    REJECTED(9, "客户退回", "客户退回要求修改，退回工程重新打样", true, false),
    CANCELLED(10, "已取消", "样品单已取消", false, true);

    private final Integer value;
    private final String label;
    private final String description;
    private final boolean editable;
    private final boolean terminal;

    SampleOrderStatusEnum(Integer value, String label, String description, boolean editable, boolean terminal) {
        this.value = value;
        this.label = label;
        this.description = description;
        this.editable = editable;
        this.terminal = terminal;
    }

    // 状态转换规则
    private static final Map<SampleOrderStatusEnum, Set<SampleOrderStatusEnum>> TRANSITIONS = new EnumMap<>(SampleOrderStatusEnum.class);

    static {
        TRANSITIONS.put(CREATED, EnumSet.of(REQUEST, CANCELLED));
        TRANSITIONS.put(REQUEST, EnumSet.of(ENGINEERING, REJECTED, CANCELLED));
        TRANSITIONS.put(ENGINEERING, EnumSet.of(SAMPLE_READY, CANCELLED));
        TRANSITIONS.put(SAMPLE_READY, EnumSet.of(SAMPLE_SENT, CANCELLED));
        TRANSITIONS.put(SAMPLE_SENT, EnumSet.of(CONFIRMED, REJECTED, CANCELLED));
        TRANSITIONS.put(REJECTED, EnumSet.of(ENGINEERING, CANCELLED));
        TRANSITIONS.put(CONFIRMED, EnumSet.of(TRANSFERRED, CLOSED, CANCELLED));
        TRANSITIONS.put(TRANSFERRED, EnumSet.noneOf(SampleOrderStatusEnum.class));
        TRANSITIONS.put(CLOSED, EnumSet.noneOf(SampleOrderStatusEnum.class));
        TRANSITIONS.put(CANCELLED, EnumSet.noneOf(SampleOrderStatusEnum.class));
    }

    private static final Map<Integer, SampleOrderStatusEnum> VALUE_MAP =
            Arrays.stream(values()).collect(Collectors.toMap(SampleOrderStatusEnum::getValue, s -> s));

    public static SampleOrderStatusEnum getByValue(Integer value) {
        SampleOrderStatusEnum status = VALUE_MAP.get(value);
        if (status == null) {
            throw new IllegalArgumentException("无效的样品单状态码: " + value);
        }
        return status;
    }

    public static Optional<SampleOrderStatusEnum> getByValueSafe(Integer value) {
        return Optional.ofNullable(VALUE_MAP.get(value));
    }

    public boolean canTransitionTo(SampleOrderStatusEnum target) {
        return TRANSITIONS.getOrDefault(this, Collections.emptySet()).contains(target);
    }

    public Set<SampleOrderStatusEnum> getNextStatuses() {
        return TRANSITIONS.getOrDefault(this, Collections.emptySet());
    }
}
