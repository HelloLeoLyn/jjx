package com.jjx.common.annotation;

import com.jjx.common.validation.QuotationItemsValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 报价明细校验：标准品、样品均可选择已有产品或填写编码和名称后随单建档。 */
@Documented
@Target({ElementType.TYPE, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = QuotationItemsValidator.class)
public @interface QuotationItemsValid {

    String message() default "报价明细必须选择已有产品或填写新产品编码和名称";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
