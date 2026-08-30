package com.jjx.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 参数校验异常枚举
 */
@Getter
@RequiredArgsConstructor
public enum ValidationExceptionEnum implements IExceptionEnum {
    
    // 通用校验
    NOT_NULL(40001, "不能为空"),
    NOT_BLANK(40002, "不能为空"),
    NOT_EMPTY(40003, "不能为空"),
    
    // 长度校验
    LENGTH_TOO_SHORT(40010, "长度过短"),
    LENGTH_TOO_LONG(40011, "长度过长"),
    SIZE_OUT_OF_RANGE(40012, "大小超出范围"),
    
    // 格式校验
    EMAIL_INVALID(40020, "邮箱格式不正确"),
    PHONE_INVALID(40021, "手机号格式不正确"),
    ID_CARD_INVALID(40022, "身份证号格式不正确"),
    DATE_INVALID(40023, "日期格式不正确"),
    
    // 数值校验
    MIN_VALUE(40030, "数值过小"),
    MAX_VALUE(40031, "数值过大"),
    DECIMAL_INVALID(40032, "小数格式不正确"),
    
    // 业务校验
    PAST_DATE(40040, "日期不能是过去时间"),
    FUTURE_DATE(40041, "日期不能是未来时间"),
    INVALID_STATUS(40042, "状态无效");
    
    private final Integer code;
    private final String message;
}