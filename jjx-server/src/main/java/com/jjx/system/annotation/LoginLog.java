package com.jjx.system.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface LoginLog {
    String loginType() default "PASSWORD";

    boolean recordOnSuccess() default true;

    boolean recordOnFailure() default true;
}
