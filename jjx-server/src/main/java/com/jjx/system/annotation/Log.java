// ==================== Log.java ====================
package com.jjx.system.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Log {
    String module() default "";

    BusinessType businessType() default BusinessType.OTHER;

    boolean saveParam() default true;

    String[] excludeParamNames() default { "password", "pwd", "token" };

    /**
     * 业务ID SpEL表达式，从参数或返回值中提取
     * 如: "#product.productId" 或 "#result.data.productId"
     */
    String bizId() default "";

    /**
     * 业务类型 SpEL表达式，从参数或返回值中提取
     * 如: "'product'" 或 "#dto.bizType"
     */
    String bizType() default "";

    /**
     * 链路追踪ID SpEL表达式
     * 从参数或返回值中提取 trace_id
     * 如: "#inquiry.traceId" 或 "#result.data.traceId"
     */
    String traceId() default "";

    /**
     * 业务状态值，如 draft / converted / approved
     * 对应实体中的 *Status 字段值
     */
    int bizStatus() default 0;
}
