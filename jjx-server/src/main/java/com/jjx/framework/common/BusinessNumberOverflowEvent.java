package com.jjx.framework.common;

/** 业务编号超过配置流水位数时发布的告警事件。 */
public record BusinessNumberOverflowEvent(
        String bizType,
        String periodKey,
        int configuredDigits,
        int actualDigits,
        long sequence) {
}
