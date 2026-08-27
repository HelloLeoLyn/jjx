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
    private final com.jjx.sales.mapper.QuotationMapper quotationMapper;
    private final com.jjx.system.mapper.SysAttachmentMapper attachmentMapper;

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
            // 返回值表达式（bizId/traceId/detail 等）必须共享同一个上下文。
            bindResult(spelCtx, result);

            // 如果参数没提取到，再从返回值提取
            if ((bizId == null || bizId.isEmpty()) && !logAnnotation.bizId().isEmpty()) {
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

            // traceId 最后回退：按 bizType+bizId 从历史操作日志继承（同一业务单据的所有操作共享 traceId）
            if ((traceId == null || traceId.isEmpty()) && bizId != null && !bizId.isEmpty()) {
                try {
                    String inherited = logSaveService.findTraceIdByBiz(bizType, bizId);
                    if (inherited != null && !inherited.isEmpty()) {
                        traceId = inherited;
                    }
                } catch (Exception ignored) {
                }
            }

            // DEV-1023：血缘反查——BOM/工艺路线/库存单据通过 source 字段继承所属订单 traceId
            if ((traceId == null || traceId.isEmpty()) && bizId != null && !bizId.isEmpty()) {
                try {
                    String inherited = logSaveService.findTraceIdBySource(bizType, bizId);
                    if (inherited != null && !inherited.isEmpty()) {
                        traceId = inherited;
                    }
                } catch (Exception ignored) {
                }
            }

            // traceId 终极回退：报价单业务，直接查 sales_quotation.trace_id
            if ((traceId == null || traceId.isEmpty()) && "quotation".equals(bizType) && bizId != null && !bizId.isEmpty()) {
                try {
                    com.jjx.sales.domain.entity.SalesQuotation q = quotationMapper.selectById(Long.valueOf(bizId));
                    if (q != null && q.getTraceId() != null && !q.getTraceId().isEmpty()) {
                        traceId = q.getTraceId();
                    }
                } catch (Exception ignored) {
                }
            }
            operLog.setBizId(bizId);
            operLog.setBizType(bizType);
            operLog.setTraceId(traceId);
            // bizStatus 支持数字字面量、SpEL 表达式以及带 getCode() 的枚举。
            try {
                operLog.setBizStatus(resolveBizStatus(spelCtx, logAnnotation.bizStatus()));
            } catch (Exception e) {
                log.warn("bizStatus解析失败: {} ({})", logAnnotation.bizStatus(), e.getMessage());
                operLog.setBizStatus(0);
            }

            // detail: attachmentIds 表达式组装附件 JSON，其他文本/JSON 原样写入。
            if (!logAnnotation.detail().isEmpty()) {
                try {
                    String detailValue = evaluateSpel(spelCtx, logAnnotation.detail());
                    if (isAttachmentDetailExpression(logAnnotation.detail())
                            && detailValue != null && !detailValue.trim().isEmpty()) {
                        java.util.List<Long> ids = parseAttachmentIds(detailValue);
                        if (!ids.isEmpty()) {
                            java.util.List<com.jjx.system.domain.entity.SysAttachment> atts = attachmentMapper.selectByIds(ids);
                            java.util.List<java.util.Map<String, Object>> attList = new java.util.ArrayList<>();
                            for (com.jjx.system.domain.entity.SysAttachment a : atts) {
                                java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                                m.put("id", a.getId());
                                m.put("fileName", a.getFileName());
                                attList.add(m);
                            }
                            if (!attList.isEmpty()) {
                                operLog.setDetail(JSONUtil.toJsonStr(java.util.Map.of("attachments", attList)));
                            }
                        }
                    } else {
                        applyDetailValue(detailValue, operLog);
                    }
                } catch (Exception e) {
                    log.warn("detail解析失败: {}", e.getMessage());
                }
            }

            if (result instanceof Result<?> resultObj) {
                if (resultObj.getCode() == 200) {
                    operLog.setStatus(YesNoEnum.YES.getCode());
                } else {
                    operLog.setStatus(YesNoEnum.NO.getCode());
                    operLog.setErrorMsg(resultObj.getMsg());
                }
                // traceId 回退：Result.data 是实体且带 getTraceId 时补上（如报价单/询价单等）
                if ((traceId == null || traceId.isEmpty()) && resultObj.getData() != null) {
                    try {
                        java.lang.reflect.Method m = resultObj.getData().getClass().getMethod("getTraceId");
                        Object val = m.invoke(resultObj.getData());
                        if (val != null && !val.toString().isEmpty()) {
                            traceId = val.toString();
                            operLog.setTraceId(traceId);
                        }
                    } catch (Exception ignored) {
                    }
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

    static void bindResult(StandardEvaluationContext ctx, Object result) {
        ctx.setVariable("result", result);
    }

    void applyDetail(StandardEvaluationContext ctx, String expression, SysOperLog operLog) {
        applyDetailValue(evaluateSpel(ctx, expression), operLog);
    }

    private static void applyDetailValue(String detailValue, SysOperLog operLog) {
        if (detailValue != null && !detailValue.trim().isEmpty()) {
            operLog.setDetail(detailValue);
        }
    }

    static boolean isAttachmentDetailExpression(String expression) {
        return expression != null && expression.matches(".*#attachmentIds\\b.*");
    }

    Integer resolveBizStatus(StandardEvaluationContext ctx, String expression) throws Exception {
        if (expression == null || expression.trim().isEmpty()) {
            return 0;
        }
        Object value;
        try {
            value = Integer.valueOf(expression);
        } catch (NumberFormatException ignored) {
            value = spelParser.parseExpression(expression).getValue(ctx);
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                Object code = value.getClass().getMethod("getCode").invoke(value);
                if (code instanceof Number number) {
                    return number.intValue();
                }
                return Integer.valueOf(String.valueOf(code));
            } catch (NoSuchMethodException ignored) {
                return Integer.valueOf(String.valueOf(value));
            }
        }
        return 0;
    }

    /**
     * 解析附件ID列表：支持 "1,2,3"、"[1,2,3]"、"1" 格式
     */
    private static List<Long> parseAttachmentIds(String raw) {
        List<Long> ids = new ArrayList<>();
        String cleaned = raw.replaceAll("[\\[\\]\\s\"]", "");
        if (cleaned.isEmpty()) {
            return ids;
        }
        for (String part : cleaned.split(",")) {
            try {
                if (!part.trim().isEmpty()) {
                    ids.add(Long.parseLong(part.trim()));
                }
            } catch (Exception ignored) {
            }
        }
        return ids;
    }
}
