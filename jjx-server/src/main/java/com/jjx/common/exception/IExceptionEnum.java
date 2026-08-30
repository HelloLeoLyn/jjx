package com.jjx.common.exception;

/**
 * 业务异常枚举统一契约：code + message。
 *
 * <p>所有业务异常枚举（BusinessExceptionEnum / ValidationExceptionEnum / 各模块 ExceptionEnum）
 * 实现本接口，即可直接传入 {@link BusinessException} 构造函数，
 * 避免再走 {@code XXXEnum.X.getCode()} / {@code .getMessage()} 手工拆包导致错误码丢失。
 */
public interface IExceptionEnum {

    /** 错误码（全局唯一，模块内分段分配） */
    Integer getCode();

    /** 错误文案 */
    String getMessage();
}
