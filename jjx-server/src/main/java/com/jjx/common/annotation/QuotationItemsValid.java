package com.jjx.common.annotation;

import com.jjx.common.validation.QuotationItemsValidator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 报价单明细校验（前后端双保险的后端部分）：
 * - 标准单（quotationType=1）：明细每行必须关联产品档案（productId 或可解析的 productCode）
 * - 样品单（quotationType=2）：明细可手填描述，不强制关联产品
 *
 * 挂在 SalesQuotation.items 字段上，配合 @Validated 触发。
 */
@Documented
@Target({ElementType.TYPE, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = QuotationItemsValidator.class)
public @interface QuotationItemsValid {

    String message() default "标准单明细必须关联产品";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
