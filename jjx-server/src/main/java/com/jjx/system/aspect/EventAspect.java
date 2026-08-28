package com.jjx.system.aspect;

import com.jjx.event.EventPublisher;
import com.jjx.system.annotation.Event;
import com.jjx.system.utils.SecurityUtils;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashMap;
import java.util.Map;

/**
 * @Event 注解的 AOP 切面
 * 拦截带 @Event 注解的方法，成功后自动发布事件
 * 有事务时等事务提交后再发布
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class EventAspect {

    private final EventPublisher eventPublisher;
    private final SpelExpressionParser spelParser = new SpelExpressionParser();

    @Around("@annotation(eventAnnotation)")
    public Object around(ProceedingJoinPoint pjp, Event eventAnnotation) throws Throwable {
        // 执行原方法
        Object result = pjp.proceed();

        // 只在成功时触发事件
        if (result instanceof Boolean && !(Boolean) result) return result;

        // 构建事件参数
        Map<String, Object> payload = new HashMap<>();

        // 当前操作者
        try {
            Long userId = SecurityUtils.getUserId();
            payload.put("triggerUserId", userId);
        } catch (Exception ignored) {
        }

        // 事件编码
        String eventCode = eventAnnotation.value();
        payload.put("eventCode", eventCode);

        // 方法参数（旧方式，保留兼容）
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        String[] paramNames = signature.getParameterNames();
        Object[] paramValues = pjp.getArgs();
        if (paramNames != null) {
            for (int i = 0; i < paramNames.length; i++) {
                payload.put(paramNames[i], paramValues[i]);
            }
        }

        // SpEL 上下文（供 bizId / params 共用）
        StandardEvaluationContext spelCtx = new StandardEvaluationContext();
        if (paramNames != null) {
            for (int i = 0; i < paramNames.length && i < paramValues.length; i++) {
                spelCtx.setVariable(paramNames[i], paramValues[i]);
            }
        }
        spelCtx.setVariable("result", result);

        // 可选发布条件，用于成功但无需重复发布的幂等返回。
        if (!eventAnnotation.condition().isEmpty()) {
            Boolean shouldPublish = spelParser.parseExpression(eventAnnotation.condition())
                    .getValue(spelCtx, Boolean.class);
            if (!Boolean.TRUE.equals(shouldPublish)) {
                return result;
            }
        }

        // 业务ID（支持SpEL表达式，如 #inquiryId / #result.inquiryNo）
        if (!eventAnnotation.bizId().isEmpty()) {
            String bizIdExpr = eventAnnotation.bizId();
            payload.put("bizId", resolveSpel(bizIdExpr, spelCtx));
        }

        // bizType
        if (!eventAnnotation.bizType().isEmpty()) {
            payload.put("bizType", eventAnnotation.bizType());
        }

        // SpEL params（新方式，覆盖旧方式同名key）
        String[] spelParams = eventAnnotation.params();
        if (spelParams != null && spelParams.length > 0) {
            for (String expr : spelParams) {
                if (expr == null || expr.trim().isEmpty()) continue;
                int eqIdx = expr.indexOf('=');
                if (eqIdx < 0) continue;
                String key = expr.substring(0, eqIdx).trim();
                String spel = expr.substring(eqIdx + 1).trim();
                try {
                    Expression exp = spelParser.parseExpression(spel);
                    Object value = exp.getValue(spelCtx);
                    payload.put(key, value);
                } catch (Exception e) {
                    log.warn("SpEL解析失败: {} (key={}, expr={})", e.getMessage(), key, spel);
                }
            }
        }

        // 返回值
        payload.put("returnValue", result);

        // 有事务时等事务提交后再发布事件
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            eventPublisher.fire(eventCode, payload);
                        }
                    }
            );
        } else {
            eventPublisher.fire(eventCode, payload);
        }

        log.debug("事件已触发: {}", eventCode);
        return result;
    }

    /**
     * 解析 SpEL 表达式；表达式不以 # 开头或解析失败时原样返回
     */
    private Object resolveSpel(String expr, StandardEvaluationContext ctx) {
        if (expr == null || expr.trim().isEmpty()) return expr;
        try {
            Expression exp = spelParser.parseExpression(expr.trim());
            return exp.getValue(ctx);
        } catch (Exception e) {
            log.warn("bizId SpEL解析失败，原样返回: {} ({})", expr, e.getMessage());
            return expr;
        }
    }
}
