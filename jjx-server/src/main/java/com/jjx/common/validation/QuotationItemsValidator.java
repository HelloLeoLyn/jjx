package com.jjx.common.validation;

import com.jjx.common.annotation.QuotationItemsValid;
import com.jjx.product.domain.entity.Product;
import com.jjx.product.mapper.ProductMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

/**
 * 报价单明细校验器：
 * - 标准单（quotationType=1）：每行明细必须能关联到产品档案
 *   （productId 直接有值，或 productCode 能在 product 表查到 → 自动补全由 service 兜底）
 * - 样品单（quotationType=2）：不强制（手填描述）
 */
@Component
public class QuotationItemsValidator implements ConstraintValidator<QuotationItemsValid, Object> {

    private final ProductMapper productMapper;

    public QuotationItemsValidator(ProductMapper productMapper) {
        this.productMapper = productMapper;
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // 明细为空时由其他校验处理（新增报价单必须带明细）
        }
        // value 可能是 SalesQuotation 实体（挂在 items 字段上时拿的是实体，需要反射取 quotationType）
        // 或直接是 List（挂在 List 参数上时拿的是明细列表，类型判断交给调用方）
        Integer quotationType = resolveQuotationType(value);
        if (quotationType != null && quotationType == 2) {
            return true; // 样品单豁免
        }
        // 标准单：校验每行
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
            if (productCode != null && !productCode.isEmpty()) {
                // 有编码但无ID：尝试查档案确认存在（ID回填由 service 兜底）
                Long matched = productMapper.selectCount(
                        new LambdaQueryWrapper<Product>().eq(Product::getProductCode, productCode)) > 0
                        ? 1L : null;
                if (matched != null) {
                    continue;
                }
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(
                        "第" + (i + 1) + "行产品：产品编码[" + productCode + "]不存在，请从产品档案选择")
                        .addConstraintViolation();
                return false;
            }
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                    "第" + (i + 1) + "行产品：标准单必须关联产品")
                    .addConstraintViolation();
            return false;
        }
        return true;
    }

    /** 从实体/参数对象里取 quotationType */
    private Integer resolveQuotationType(Object value) {
        try {
            if (value instanceof Map) {
                Object t = ((Map<?, ?>) value).get("quotationType");
                return t == null ? null : Integer.valueOf(t.toString());
            }
            Field f = value.getClass().getDeclaredField("quotationType");
            f.setAccessible(true);
            Object v = f.get(value);
            return v == null ? null : Integer.valueOf(v.toString());
        } catch (Exception e) {
            return null;
        }
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
