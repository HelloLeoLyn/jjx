package com.jjx.sales.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 操作结果枚举
 * 对应数据库 operation_result 字段 (tinyint)
 */
@Getter
public enum OperationResultEnum {

    SUCCESS(1, "成功"),
    FAILURE(2, "失败");

    private final Integer code;
    private final String name;

    OperationResultEnum(Integer code, String name) {
        this.code = code;
        this.name = name;
    }

    private static final Map<Integer, OperationResultEnum> CODE_MAP =
            Arrays.stream(values()).collect(Collectors.toMap(OperationResultEnum::getCode, e -> e));

    public static OperationResultEnum getByCode(Integer code) {
        OperationResultEnum result = CODE_MAP.get(code);
        if (result == null) {
            return SUCCESS;
        }
        return result;
    }
}