package com.jjx.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * {@link ValidContactPhone} 的实现：先判长度再判字符白名单，分别给出明确提示。
 */
public class ContactPhoneValidator implements ConstraintValidator<ValidContactPhone, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isEmpty()) {
            return true; // 可选字段
        }
        if (value.length() > ContactPhoneRule.MAX_LENGTH) {
            return fail(context, ContactPhoneRule.LENGTH_MESSAGE);
        }
        if (!ContactPhoneRule.PATTERN.matcher(value).matches()) {
            return fail(context, ContactPhoneRule.CHARSET_MESSAGE);
        }
        return true;
    }

    private boolean fail(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
        return false;
    }
}
