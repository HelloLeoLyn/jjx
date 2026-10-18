package com.jjx.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 联系电话统一校验（dev-20261018-026）：可选、≤50 字符、字符白名单，不做手机号/座机格式判断。
 *
 * <p>客户、报价单、销售订单 DTO 统一使用本注解，规则与提示语只在 {@link ContactPhoneRule} 维护一处。
 */
@Documented
@Constraint(validatedBy = ContactPhoneValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidContactPhone {

    String message() default ContactPhoneRule.CHARSET_MESSAGE;

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
