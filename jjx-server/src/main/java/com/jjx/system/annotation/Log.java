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
    String[] excludeParamNames() default {"password", "pwd", "token"};
}
