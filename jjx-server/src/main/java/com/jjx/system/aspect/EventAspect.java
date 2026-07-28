package com.jjx.system.aspect;

import com.jjx.event.EventPublisher;
import com.jjx.system.annotation.Event;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
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

    @Around("@annotation(eventAnnotation)")
    public Object around(ProceedingJoinPoint pjp, Event eventAnnotation) throws Throwable {
        // 执行原方法
        Object result = pjp.proceed();

        // 只在成功时触发事件
        if (result instanceof Boolean && !(Boolean) result) return result;

        // 构建事件参数
        Map<String, Object> payload = new HashMap<>();

        // 事件编码
        String eventCode = eventAnnotation.value();
        payload.put("eventCode", eventCode);

        // 业务ID
        if (!eventAnnotation.bizId().isEmpty()) {
            payload.put("bizId", eventAnnotation.bizId());
        }

        // bizType
        if (!eventAnnotation.bizType().isEmpty()) {
            payload.put("bizType", eventAnnotation.bizType());
        }

        // 方法参数
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        String[] paramNames = signature.getParameterNames();
        Object[] paramValues = pjp.getArgs();
        if (paramNames != null) {
            for (int i = 0; i < paramNames.length; i++) {
                payload.put(paramNames[i], paramValues[i]);
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
}
