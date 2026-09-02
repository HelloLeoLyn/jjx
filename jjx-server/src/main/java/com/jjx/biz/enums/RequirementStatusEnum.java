package com.jjx.biz.enums;

import com.jjx.common.enums.BizStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 业务需求单状态
 * 1草稿 → 2评审中 → 3已通过 → 4执行中 → 5已关闭
 *                ↘ 6已驳回
 */
@Getter
@AllArgsConstructor
public enum RequirementStatusEnum implements BizStatusEnum {
    DRAFT(1, "草稿"),
    REVIEWING(2, "评审中"),
    APPROVED(3, "已通过"),
    EXECUTING(4, "执行中"),
    CLOSED(5, "已关闭"),
    REJECTED(6, "已驳回");

    private final Integer value;
    private final String label;

    public static RequirementStatusEnum getByValue(Integer value) {
        if (value == null) return null;
        for (RequirementStatusEnum s : values()) {
            if (s.value.equals(value)) return s;
        }
        return null;
    }
}
