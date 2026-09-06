package com.jjx.production.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum QualityReviewStatusEnum {
    DRAFT("DRAFT", "待提交"),
    PENDING("PENDING", "待审核"),
    APPROVED("APPROVED", "已审核"),
    REJECTED("REJECTED", "已驳回");

    private final String code;
    private final String label;
}
