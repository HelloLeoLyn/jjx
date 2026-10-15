package com.jjx.sales.service.impl;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import com.jjx.common.exception.BusinessException;

/** 标准BOM数量口径；历史小数用量转换为约分后的整数基数/模数。 */
public final class SampleMaterialQuantity {
    private SampleMaterialQuantity() {}
    public static BigDecimal[] normalize(BigDecimal base, BigDecimal module, BigDecimal legacy) {
        if (base == null && module == null) {
            BigDecimal value = legacy == null ? BigDecimal.ONE : legacy;
            if (value.signum() <= 0 || value.scale() > 4) {
                throw new BusinessException("历史用量需确认正数且最多4位小数后才能保存/转移");
            }
            value = value.stripTrailingZeros();
            BigInteger denominator = BigInteger.TEN.pow(Math.max(0, value.scale()));
            BigInteger numerator = value.multiply(new BigDecimal(denominator)).toBigIntegerExact();
            BigInteger gcd = numerator.gcd(denominator);
            base = new BigDecimal(numerator.divide(gcd));
            module = new BigDecimal(denominator.divide(gcd));
        }
        if (base == null || module == null || base.compareTo(BigDecimal.ONE) < 0 || module.compareTo(BigDecimal.ONE) < 0
                || base.stripTrailingZeros().scale() > 0 || module.stripTrailingZeros().scale() > 0
                || base.precision() - base.scale() > 12 || module.precision() - module.scale() > 12) {
            throw new BusinessException("模数和基数须为至少1的整数（最多12位）");
        }
        return new BigDecimal[] { base, module, base.divide(module, 4, RoundingMode.HALF_UP) };
    }
}
