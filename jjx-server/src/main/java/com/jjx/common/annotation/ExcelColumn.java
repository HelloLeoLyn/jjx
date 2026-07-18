package com.jjx.common.annotation;

import java.lang.annotation.*;




/**
 * Excel 列注解
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ExcelColumn {

    /**
     * 表头名称
     */
    String value() default "";

    /**
     * 列顺序（数值越小越靠前）
     */
    int order() default 0;

    /**
     * 是否必填（导入时校验）
     */
    boolean required() default false;

    /**
     * 字段说明（用于模板提示）
     */
    String comment() default "";
}