package com.jjx.production.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 生产工序执行的业务类型。 */
@Getter
@RequiredArgsConstructor
public enum ExecutionTypeEnum {
    NORMAL("NORMAL", "正常执行"),
    REWORK("REWORK", "返工执行");

    private final String code;
    private final String label;
}
