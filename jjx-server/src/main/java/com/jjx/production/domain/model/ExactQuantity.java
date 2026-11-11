package com.jjx.production.domain.model;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * 精确有理数（dev-20261111-006）。
 *
 * <p>分子/分母使用 BigInteger，构造时约简并保持分母为正；从数据库 decimal 读取时按
 * unscaled/10^scale 精确转换，不经过 double，也不在中间层转 decimal 或取整。</p>
 *
 * <p>展示用近似值由 {@link #approximate()} 单独生成，不能作为制造/采购/库存的权威输入。</p>
 */
public final class ExactQuantity implements Comparable<ExactQuantity> {

    public static final ExactQuantity ZERO = new ExactQuantity(BigInteger.ZERO, BigInteger.ONE);
    public static final ExactQuantity ONE = new ExactQuantity(BigInteger.ONE, BigInteger.ONE);

    private static final int DISPLAY_SCALE = 12;

    private final BigInteger numerator;
    private final BigInteger denominator;

    private ExactQuantity(BigInteger numerator, BigInteger denominator) {
        if (numerator == null) {
            throw new IllegalArgumentException("精确数量分子不能为空");
        }
        if (denominator == null || denominator.signum() == 0) {
            throw new IllegalArgumentException("精确数量分母不能为0");
        }
        if (denominator.signum() < 0) {
            numerator = numerator.negate();
            denominator = denominator.negate();
        }
        BigInteger gcd = numerator.gcd(denominator);
        if (!BigInteger.ONE.equals(gcd)) {
            numerator = numerator.divide(gcd);
            denominator = denominator.divide(gcd);
        }
        this.numerator = numerator;
        this.denominator = denominator;
    }

    public static ExactQuantity of(long value) {
        return new ExactQuantity(BigInteger.valueOf(value), BigInteger.ONE);
    }

    public static ExactQuantity of(BigInteger numerator, BigInteger denominator) {
        return new ExactQuantity(numerator, denominator);
    }

    /** decimal(scale) → unscaled / 10^scale，精确无舍入。 */
    public static ExactQuantity fromDecimal(BigDecimal value) {
        if (value == null) {
            throw new IllegalArgumentException("精确数量来源不能为空");
        }
        BigDecimal normalized = value.stripTrailingZeros();
        int scale = normalized.scale();
        BigInteger unscaled = normalized.unscaledValue();
        if (scale >= 0) {
            return new ExactQuantity(unscaled, BigInteger.TEN.pow(scale));
        }
        return new ExactQuantity(unscaled.multiply(BigInteger.TEN.pow(-scale)), BigInteger.ONE);
    }

    public ExactQuantity add(ExactQuantity other) {
        Objects.requireNonNull(other, "加数不能为空");
        return new ExactQuantity(
                numerator.multiply(other.denominator).add(other.numerator.multiply(denominator)),
                denominator.multiply(other.denominator));
    }

    public ExactQuantity subtract(ExactQuantity other) {
        Objects.requireNonNull(other, "减数不能为空");
        return new ExactQuantity(
                numerator.multiply(other.denominator).subtract(other.numerator.multiply(denominator)),
                denominator.multiply(other.denominator));
    }

    public ExactQuantity multiply(ExactQuantity other) {
        Objects.requireNonNull(other, "乘数不能为空");
        return new ExactQuantity(numerator.multiply(other.numerator), denominator.multiply(other.denominator));
    }

    public ExactQuantity divide(ExactQuantity other) {
        Objects.requireNonNull(other, "除数不能为空");
        if (other.isZero()) {
            throw new IllegalArgumentException("精确数量除数不能为0");
        }
        return new ExactQuantity(numerator.multiply(other.denominator), denominator.multiply(other.numerator));
    }

    public int signum() {
        return numerator.signum();
    }

    public boolean isZero() {
        return numerator.signum() == 0;
    }

    public boolean isPositive() {
        return numerator.signum() > 0;
    }

    public boolean isNegative() {
        return numerator.signum() < 0;
    }

    public boolean isInteger() {
        return BigInteger.ONE.equals(denominator);
    }

    public BigInteger abs() {
        return numerator.abs();
    }

    /** 仅用于最终聚合展示：向上取整，不在中间层调用。 */
    public BigInteger ceilToInteger() {
        BigInteger[] quotientAndRemainder = numerator.divideAndRemainder(denominator);
        if (quotientAndRemainder[1].signum() > 0) {
            return quotientAndRemainder[0].add(BigInteger.ONE);
        }
        return quotientAndRemainder[0];
    }

    public BigDecimal toBigDecimal(int scale, RoundingMode roundingMode) {
        return new BigDecimal(numerator).divide(new BigDecimal(denominator), scale, roundingMode);
    }

    /** 12 位小数近似值，仅用于展示。 */
    public BigDecimal approximate() {
        return toBigDecimal(DISPLAY_SCALE, RoundingMode.HALF_UP);
    }

    public BigDecimal approximate(int scale) {
        return toBigDecimal(scale, RoundingMode.HALF_UP);
    }

    /** 权威精确比例文本，例如 "1/6"。 */
    public String exactText() {
        return numerator + "/" + denominator;
    }

    /** 去掉无意义末尾零的近似文本。 */
    public String approximateText() {
        BigDecimal value = approximate();
        if (value.signum() == 0) {
            return "0";
        }
        return value.stripTrailingZeros().toPlainString();
    }

    @Override
    public int compareTo(ExactQuantity other) {
        if (other == null) {
            throw new IllegalArgumentException("比较对象不能为空");
        }
        return numerator.multiply(other.denominator).compareTo(other.numerator.multiply(denominator));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ExactQuantity that)) {
            return false;
        }
        return numerator.equals(that.numerator) && denominator.equals(that.denominator);
    }

    @Override
    public int hashCode() {
        return Objects.hash(numerator, denominator);
    }

    @Override
    public String toString() {
        return exactText();
    }
}
