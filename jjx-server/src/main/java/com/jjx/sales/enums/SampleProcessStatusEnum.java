package com.jjx.sales.enums;

/** 与前端 SampleProcessStatusEnum 同一状态口径。 */
public enum SampleProcessStatusEnum {
    PENDING(0), IN_PROGRESS(1), COMPLETED(2);
    private final Integer value;
    SampleProcessStatusEnum(int value) { this.value=value; }
    public Integer getValue() { return value; }
}
