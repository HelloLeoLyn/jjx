// ==================== OperLogAspect.java ====================
package com.jjx.system.aspect;

import cn.hutool.json.JSONUtil;
import com.jjx.common.core.result.Result;
import com.jjx.common.enums.YesNoEnum;
import com.jjx.system.annotation.Log;
import com.jjx.system.domain.entity.SysOperLog;
import com.jjx.system.service.LogSaveService;
import com.jjx.system.utils.IpUtils;
import com.jjx.system.utils.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperLogAspect {

    private final LogSaveService logSaveService;
    private final SpelExpressionParser spelParser = new SpelExpressionParser();

    @Around("@annotation(logAnnotation)")
    public Object around(ProceedingJoinPoint point, Log logAnnotation) throws Throwable {
        long startTime = System.currentTimeMillis();
        SysOperLog operLog = new SysOperLog();

        try {
            // 填充基础信息
            fillBaseInfo(operLog, logAnnotation);

            // 处理请求参数
            if (logAnnotation.saveParam()) {
                operLog.setOperParam(getRequestParams(point, logAnnotation));
            }

            // 准备SpEL上下文（方法参数）
            Object[] args = point.getArgs();
            StandardEvaluationContext spelCtx = new StandardEvaluationContext();
            String[] paramNames = ((MethodSignature) point.getSignature()).getParameterNames();
            if (paramNames != null) {
                for (int i = 0; i < paramNames.length && i < args.length; i++) {
                    spelCtx.setVariable(paramNames[i], args[i]);
                }
            }

            // 先从参数提取bizId/bizType/traceId
            String bizId = evaluateSpel(spelCtx, logAnnotation.bizId());
            String bizType = evaluateSpel(spelCtx, logAnnotation.bizType());
            String traceId = evaluateSpel(spelCtx, logAnnotation.traceId());

            // 执行业务方法
            Object result = point.proceed();

            // 如果参数没提取到，再从返回值提取
            if ((bizId == null || bizId.isEmpty()) && !logAnnotation.bizId().isEmpty()) {
                spelCtx.setVariable("result", result);
                bizId = evaluateSpel(spelCtx, logAnnotation.bizId());
            }
            if ((bizType == null || bizType.isEmpty()) && !logAnnotation.bizType().isEmpty()) {
                spelCtx.setVariable("result", result);
                bizType = evaluateSpel(spelCtx, logAnnotation.bizType());
            }
            if ((traceId == null || traceId.isEmpty()) && !logAnnotation.traceId().isEmpty()) {
                spelCtx.setVariable("result", result);
                traceId = evaluateSpel(spelCtx, logAnnotation.traceId());
            }

            // traceId 优先用 SpEL，没有则扫描参数中的实体对象（如 SalesInquiry.getTraceId()）
            if ((traceId == null || traceId.isEmpty()) && args != null) {
                for (Object arg : args) {
                    if (arg == null)
                        continue;
                    try {
                        java.lang.reflect.Method m = arg.getClass().getMethod("getTraceId");
                        Object val = m.invoke(arg);
                        if (val != null && !val.toString().isEmpty()) {
                            traceId = val.toString();
                            break;
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
            operLog.setBizId(bizId);
            operLog.setBizType(bizType);
            operLog.setTraceId(traceId);

            if (result instanceof Result<?> resultObj) {
                if (resultObj.getCode() == 200) {
                    operLog.setStatus(YesNoEnum.YES.getCode());
                } else {
                    operLog.setStatus(YesNoEnum.NO.getCode());
                    operLog.setErrorMsg(resultObj.getMsg());
                }
            } else {
                operLog.setStatus(3);
            }
            return result;

        } catch (Exception e) {
            operLog.setStatus(0);
            operLog.setErrorMsg(truncate(e.getMessage(), 500));
            throw e;
        } finally {
            operLog.setCostTime(System.currentTimeMillis() - startTime);
            LocalDateTime now = LocalDateTime.now();
            operLog.setCreateTime(now);
            logSaveService.saveOperLog(operLog);
        }
    }

    private static void fillBaseInfo(SysOperLog operLog, Log logAnnotation) {
        // 用户信息
        operLog.setUserId(SecurityUtils.getUserId());
        operLog.setUsername(SecurityUtils.getUsername());
        operLog.setRealName(SecurityUtils.getRealName());
        operLog.setTenantId(SecurityUtils.getTenantId());

        // 注解信息
        operLog.setModule(logAnnotation.module());
        operLog.setBusinessType(logAnnotation.businessType().getCode());

        // 请求信息
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            operLog.setOperUrl(request.getRequestURI());
            operLog.setOperIp(IpUtils.getClientIp(request));
            operLog.setUserAgent(truncate(request.getHeader("User-Agent"), 500));
        }
    }

    private static String getRequestParams(ProceedingJoinPoint point, Log logAnnotation) {
        Object[] args = point.getArgs();
        if (args == null || args.length == 0) {
            return "";
        }

        String[] paramNames = ((MethodSignature) point.getSignature()).getParameterNames();
        Set<String> excludeSet = new HashSet<>(Arrays.asList(logAnnotation.excludeParamNames()));

        Map<String, Object> paramMap = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            // 跳过 Request/Response 对象
            if (arg instanceof HttpServletRequest || arg instanceof HttpServletResponse) {
                continue;
            }
            String paramName = paramNames != null && i < paramNames.length ? paramNames[i] : "arg" + i;
            if (!excludeSet.contains(paramName)) {
                paramMap.put(paramName, arg);
            }
        }
        return truncate(toJSONString(paramMap), 2000);
    }

    private static String truncate(String str, int maxLength) {
        if (str == null) {
            return "";
        }
        return str.length() > maxLength ? str.substring(0, maxLength) + "..." : str;
    }

    private static String toJSONString(Map<String, Object> paramMap) {
        return JSONUtil.toJsonStr(paramMap);
    }

    /**
     * 安全解析SpEL表达式，返回String值（失败返回空字符串）
     */
    private String evaluateSpel(StandardEvaluationContext ctx, String expression) {
        if (expression == null || expression.trim().isEmpty()) {
            return null;
        }
        try {
            Expression exp = spelParser.parseExpression(expression);
            Object value = exp.getValue(ctx);
            return value == null ? null : String.valueOf(value);
        } catch (Exception e) {
            log.warn("SpEL解析失败: expression={}, error={}", expression, e.getMessage());
            return null;
        }
    }
}
