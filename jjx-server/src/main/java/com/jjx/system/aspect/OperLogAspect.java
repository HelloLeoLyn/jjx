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

    /** 表达式缓存：注解上的表达式是有限集合，避免每次调用都重新 parse */
    private final java.util.concurrent.ConcurrentHashMap<String, Expression> spelCache =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** sys_oper_log.biz_status 列宽 varchar(200) */
    private static final int BIZ_STATUS_MAX_LENGTH = 200;

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

            // bizStatus 必须落真实状态，放在成功/失败判定之后处理：
            // 操作成功却取不到状态 = 注解写错或方法没把状态带出来，直接抛错，不允许静默落空；
            // 操作本身失败时状态没变，取不到属正常，留空即可。
            boolean succeeded = YesNoEnum.YES.getCode().equals(operLog.getStatus());
            try {
                operLog.setBizStatus(resolveBizStatus(spelCtx, logAnnotation.bizStatus()));
            } catch (Exception e) {
                if (succeeded) {
                    throw new IllegalStateException(String.format(
                            "@Log bizStatus 解析失败: method=%s, expression=%s",
                            point.getSignature().toShortString(), logAnnotation.bizStatus()), e);
                }
                log.warn("操作失败且 bizStatus 取不到，留空: method={}, expression={}",
                        point.getSignature().toShortString(), logAnnotation.bizStatus());
                operLog.setBizStatus("");
            }
            if (succeeded && (operLog.getBizStatus() == null || operLog.getBizStatus().isEmpty())) {
                throw new IllegalStateException(String.format(
                        "@Log bizStatus 求值为空: method=%s, expression=%s",
                        point.getSignature().toShortString(), logAnnotation.bizStatus()));
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
            Expression exp = parseCached(expression);
            Object value = exp.getValue(ctx);
            return value == null ? null : String.valueOf(value);
        } catch (Exception e) {
            log.warn("SpEL解析失败: expression={}, error={}", expression, e.getMessage());
            return null;
        }
    }

    /** 表达式只 parse 一次，之后复用（注解上的表达式是有限集合） */
    private Expression parseCached(String expression) {
        return spelCache.computeIfAbsent(expression, spelParser::parseExpression);
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

    String resolveBizStatus(StandardEvaluationContext ctx, String expression) throws Exception {
        if (expression == null || expression.trim().isEmpty()) {
            return "";
        }
        String trimmed = expression.trim();

        // 1) 纯字面量（"3" / "DRAFT" / "PENDING_REVIEW"）：原样落库，不进 SpEL，零反射。
        //    biz_status 已是 varchar，注解写什么库里就是什么。
        if (isLiteralBizStatus(trimmed)) {
            return clampBizStatus(trimmed);
        }

        // 2) 其余按 SpEL 求值（#result.data.bizStatus / #dto.status / T(枚举).X...）
        Object value = parseCached(trimmed).getValue(ctx);
        if (value == null) {
            // 表达式合法但取不到值（方法没把状态带出来）——返回空串，由调用方按成功/失败决定是否抛错
            return "";
        }

        // 3) 求值结果是状态枚举常量（注解写成 T(枚举).X 而没有调 getLabel()）：
        //    统一契约后直接按接口取文案，不再反射。biz_status 是 varchar，存的就是 label。
        if (value instanceof com.jjx.common.enums.BizStatusEnum statusEnum) {
            return clampBizStatus(statusEnum.getLabel());
        }
        if (value instanceof Enum<?> enumValue) {
            // 未接入 BizStatusEnum 的枚举（类型/是否 之类）：落枚举名，至少可读
            return clampBizStatus(enumValue.name());
        }

        return clampBizStatus(String.valueOf(value));
    }

    /**
     * 是否为纯字面量：不含 SpEL 变量引用（#）也不含类型引用（T(...)）。
     * 命中则直接落库，既省掉一次 SpEL 解析，也让 "DRAFT" 这种语义值不会被当表达式解析失败。
     */
    static boolean isLiteralBizStatus(String expression) {
        return expression != null && !expression.contains("#") && !expression.contains("T(");
    }


    /** 截到列宽，不加省略号——状态值被截断也要保持是个干净的值 */
    private static String clampBizStatus(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > BIZ_STATUS_MAX_LENGTH ? value.substring(0, BIZ_STATUS_MAX_LENGTH) : value;
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
