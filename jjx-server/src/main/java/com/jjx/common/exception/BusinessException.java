package com.jjx.common.exception;

import lombok.Getter;

/**
 * 业务异常类
 */
@Getter
public class BusinessException extends RuntimeException{
    
    private final Integer code;
    private final String message;
    
    public BusinessException(String message) {
        super(message);
        code = BusinessExceptionEnum.FAIL.getCode();
        this.message = message;
    }
    
    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }
    
    public BusinessException(BusinessExceptionEnum exceptionEnum) {
        super(exceptionEnum.getMessage());
        code = exceptionEnum.getCode();
        message = exceptionEnum.getMessage();
    }
    
    public BusinessException(BusinessExceptionEnum exceptionEnum, String message) {
        super(message);
        code = exceptionEnum.getCode();
        this.message = message;
    }
    
    public BusinessException(String message, Throwable cause) {
        super(message, cause);
        code = BusinessExceptionEnum.FAIL.getCode();
        this.message = message;
    }
    
    public BusinessException(BusinessExceptionEnum exceptionEnum, Throwable cause) {
        super(exceptionEnum.getMessage(), cause);
        code = exceptionEnum.getCode();
        message = exceptionEnum.getMessage();
    }
}