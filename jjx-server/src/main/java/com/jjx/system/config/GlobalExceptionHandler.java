package com.jjx.system.config;

import cn.dev33.satoken.exception.NotLoginException;
import com.jjx.common.core.result.Result;
import com.jjx.common.exception.BusinessException;
import com.jjx.common.exception.BusinessExceptionEnum;
import com.jjx.system.domain.entity.SysErrorLog;
import com.jjx.system.service.LogSaveService;
import com.jjx.system.utils.IpUtils;
import com.jjx.system.utils.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.MethodValidationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final LogSaveService logSaveService;


    @ExceptionHandler(NotLoginException.class)
    public Result<Void> handleNotLoginException(NotLoginException e) {
        return Result.error(BusinessExceptionEnum.UNAUTHORIZED.getCode(),
                BusinessExceptionEnum.UNAUTHORIZED.getMessage());
    }

    /**
     * "org.springframework.web.method.annotation.HandlerMethodValidationException: 400 BAD_REQUEST "Validation failure""
     */
    @ExceptionHandler({MethodArgumentNotValidException.class, MethodValidationException.class})
    public Result<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", message);
        return Result.error(BusinessExceptionEnum.VALIDATION_FAILED.getCode(), message);
    }

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: {} - {}", e.getCode(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public Result<Void> handleRuntimeException(RuntimeException e, HttpServletRequest request) {
        e.printStackTrace();
        log.warn("运行时异常: {} - {}", e.getCause(), e.getMessage());
        recordErrorLog(e, request);
        return Result.error(BusinessExceptionEnum.FAIL.getCode(),
                e.getMessage() != null ? e.getMessage() : "运行时异常");
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e, HttpServletRequest request) {
        String traceId = UUID.randomUUID().toString().replace("-", "");
        MDC.put("traceId", traceId);
        recordErrorLog(e, request);
        log.error("系统异常 - traceId: {}", traceId, e);
        return Result.error(BusinessExceptionEnum.SYSTEM_ERROR.getCode(),
                BusinessExceptionEnum.SYSTEM_ERROR.getMessage());
    }

    private void recordErrorLog(Exception e, HttpServletRequest request) {
        try {
            SysErrorLog errorLog = new SysErrorLog();
            errorLog.setTraceId(MDC.get("traceId"));
            errorLog.setUserId(SecurityUtils.getUserId());
            errorLog.setUsername(SecurityUtils.getUsername());
            errorLog.setExceptionName(e.getClass().getName());
            errorLog.setExceptionMsg(truncateExceptionMsg(e));
            errorLog.setRequestUrl(request.getRequestURI());
            errorLog.setRequestMethod(request.getMethod());
            errorLog.setClientIp(IpUtils.getClientIp(request));
            errorLog.setTriggerTime(LocalDateTime.now());
            errorLog.setHandleStatus(0);
            logSaveService.saveErrorLog(errorLog);
        } catch (Exception ex) {
            log.error("记录错误日志失败", ex);
        }
    }

    /**
     * 截取异常消息
     */
    private static String truncateExceptionMsg(Exception e) {
        return truncate(switch (e) {
            case MethodArgumentNotValidException ex ->
                    ex.getBindingResult().getFieldErrors().stream()
                            .map(FieldError::getDefaultMessage)
                            .collect(Collectors.joining("; "));
            case ConstraintViolationException ex ->
                    ex.getConstraintViolations().stream()
                            .map(ConstraintViolation::getMessage)
                            .collect(Collectors.joining("; "));
            case BindException ex ->
                    ex.getFieldErrors().stream()
                            .map(FieldError::getDefaultMessage)
                            .collect(Collectors.joining("; "));
            default -> e.getMessage();
        }, 500);
    }

    /**
     * 截取字符串
     */
    private static String truncate(String str, int maxLength) {
        if (str == null || str.length() <= maxLength) {
            return str == null ? "" : str;
        }
        return str.substring(0, maxLength) + "...";
    }
}