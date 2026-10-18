package com.jjx.common.validation;

import com.jjx.common.annotation.QuotationItemsValid;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.List;

/** 报价明细允许关联已有产品，或提交编码及名称后随报价建档；两种报价类型共用。 */
@Component
public class QuotationItemsValidator implements ConstraintValidator<QuotationItemsValid, Object> {

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // 明细为空时由其他校验处理（新增报价单必须带明细）
        }
        List<?> items = extractItems(value);
        if (items == null) {
            return true;
        }
        for (int i = 0; i < items.size(); i++) {
            Object item = items.get(i);
            Long productId = getFieldValue(item, "productId", Long.class);
            String productCode = getFieldValue(item, "productCode", String.class);
            if (productId != null) {
                continue;
            }
            String productName = getFieldValue(item, "productName", String.class);
            if (productCode != null && !productCode.isBlank()
                    && productName != null && !productName.isBlank()) {
                continue;
            }
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                    "第" + (i + 1) + "行产品：请选择已有产品或填写新产品编码和名称")
                    .addConstraintViolation();
            return false;
        }
        return true;
    }

    /** 提取明细列表：实体上取 items 字段，或直接是 List */
    @SuppressWarnings("unchecked")
    private List<?> extractItems(Object value) {
        if (value instanceof List) {
            return (List<?>) value;
        }
        try {
            Field f = value.getClass().getDeclaredField("items");
            f.setAccessible(true);
            Object v = f.get(value);
            return v instanceof List ? (List<?>) v : null;
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T getFieldValue(Object obj, String name, Class<T> type) {
        try {
            Field f = obj.getClass().getDeclaredField(name);
            f.setAccessible(true);
            Object v = f.get(obj);
            return v == null ? null : (T) v;
        } catch (Exception e) {
            return null;
        }
    }
}
