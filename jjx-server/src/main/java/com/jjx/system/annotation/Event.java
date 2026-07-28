package com.jjx.system.annotation;

import java.lang.annotation.*;

/**
 * 业务事件注解
 * 标记在方法上，方法执行成功后自动触发对应事件
 * 事件驱动：日志记录 + 消息通知 + 任务创建
 *
 * <pre>
 * // 90%场景：用注解，自动触发
 * {@code @Event("product.approved")}
 * public boolean approveProduct(ProductUpdateDTO dto) { ... }
 *
 * // 10%场景：需要传局部变量时，手动调 EventPublisher
 * eventPublisher.fire("product.approved", Map.of("productName", name));
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Event {
    /** 事件编码，如 product.approved */
    String value();

    /** 业务ID，支持SpEL表达式，如 #dto.productId */
    String bizId() default "";

    /** 业务类型，如 product */
    String bizType() default "";

    /**
     * 自定义事件参数（SpEL表达式）
     * 格式: "key = #spelExpression"
     * 如: "orderNo = #order.orderNo"
     * 自动合并到事件payload中
     */
    String[] params() default {};
}
