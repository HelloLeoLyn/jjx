package com.jjx.purchase.domain.model;

import com.jjx.common.exception.BusinessException;
import com.jjx.common.util.UnitCodes;
import com.jjx.production.domain.model.ExactQuantity;
import com.jjx.purchase.domain.dto.PurchaseOrderItemDTO;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.Set;

/** 采购预览/保存共用；以原始数量和尺寸精确换算，终点才舍入。 */
public final class PurchaseQuantityPricing {
    private PurchaseQuantityPricing() { }
    private static final String AREA = UnitCodes.SQM;
    private static final Set<String> PIECES = Set.of(UnitCodes.PCS, UnitCodes.SHEET);
    private static final ExactQuantity THOUSAND = ExactQuantity.of(1000);
    private static ExactQuantity exact(BigDecimal n) { return ExactQuantity.fromDecimal(n); }

    public static void bounded(BigDecimal value, int scale, int integers, String label, boolean positive) {
        if (value == null) return;
        if ((positive ? value.signum() <= 0 : value.signum() < 0)
                || value.stripTrailingZeros().scale() > scale
                || (long)value.precision() - value.scale() > integers)
            throw new BusinessException(label + (positive ? "须大于0" : "不能为负数")
                    + "，最多" + scale + "位小数、" + integers + "位整数");
    }
    private static BigDecimal required(BigDecimal n, String label) {
        if (n == null || n.signum() <= 0) throw new BusinessException("请补齐大于0的" + label);
        return n;
    }
    private static ExactQuantity width(PurchaseOrderItemDTO i) {
        return exact(required(i.getRollWidthMm(), "宽度(mm)")).divide(THOUSAND);
    }
    private static ExactQuantity length(PurchaseOrderItemDTO i) {
        return exact(required(i.getRollLengthM(), "每卷长度(m)"));
    }
    private static ExactQuantity pieceArea(PurchaseOrderItemDTO i) {
        return exact(required(i.getPieceWidthMm(), "每片宽度(mm)"))
                .multiply(exact(required(i.getPieceLengthMm(), "每片长度(mm)")))
                .divide(ExactQuantity.of(1000000));
    }
    /** 不把PCS、张、针、套等不同计数单位直接视作1:1。 */
    public static void validateCombination(String base, String purchase, String price) {
        Set<String> units = availableUnits(base);
        if (!units.contains(purchase) || !units.contains(price))
            throw new BusinessException("采购单位与报价单位缺少受支持的换算依据，请核对本单计量方式");
        if ((UnitCodes.ROLL.equals(purchase) || UnitCodes.ROLL.equals(price))
                && (PIECES.contains(purchase) || PIECES.contains(price)))
            throw new BusinessException("卷与片不能仅凭面积直接互换，请分别确认采购明细");
    }
    public static Set<String> availableUnits(String base) {
        base = UnitCodes.requireDictUnit(base,"物料基本单位");
        return AREA.equals(base) ? Set.of(AREA,UnitCodes.M,UnitCodes.ROLL,UnitCodes.PCS,UnitCodes.SHEET)
                : UnitCodes.M.equals(base) ? Set.of(AREA,UnitCodes.M,UnitCodes.ROLL)
                : PIECES.contains(base) ? Set.of(base,AREA) : Set.of(base);
    }
    private static ExactQuantity toBase(ExactQuantity qty, String unit, String base, PurchaseOrderItemDTO i) {
        if (unit.equals(base)) return qty;
        if (AREA.equals(base)) {
            if (UnitCodes.ROLL.equals(unit)) return qty.multiply(length(i)).multiply(width(i));
            if (UnitCodes.M.equals(unit)) return qty.multiply(width(i));
            if (PIECES.contains(unit)) return qty.multiply(pieceArea(i));
        }
        if (UnitCodes.M.equals(base)) {
            if (UnitCodes.ROLL.equals(unit)) return qty.multiply(length(i));
            if (AREA.equals(unit)) return qty.divide(width(i));
        }
        if (PIECES.contains(base) && AREA.equals(unit)) return qty.divide(pieceArea(i));
        throw new BusinessException("采购数量无法换算为库存基本单位");
    }
    private static ExactQuantity fromBase(ExactQuantity qty, String base, String unit, PurchaseOrderItemDTO i) {
        if (unit.equals(base)) return qty;
        // 以一个报价单位对应的基本数量作为依据；数量保持精确分数。
        return qty.divide(toBase(ExactQuantity.ONE, unit, base, i));
    }
    private static void requireWhole(ExactQuantity qty, String label) {
        if (!qty.isInteger()) throw new BusinessException(label + "必须是整数，请核对实际片数或尺寸，不能舍入入库");
    }
    public static void calculate(PurchaseOrderItemDTO i) {
        String base = UnitCodes.requireDictUnit(i.getUnit(), "物料基本单位");
        String purchase = UnitCodes.requireDictUnit(blank(i.getPurchaseUnit()) ? base : i.getPurchaseUnit(), "采购单位");
        String price = UnitCodes.requireDictUnit(blank(i.getPriceUnit()) ? base : i.getPriceUnit(), "报价单位");
        validateCombination(base, purchase, price);
        i.setUnit(base); i.setPurchaseUnit(purchase); i.setPriceUnit(price);
        bounded(i.getRollLengthM(), 4, 8, "每卷长度", true);
        bounded(i.getRollWidthMm(), 2, 10, "宽度", true);
        bounded(i.getPieceLengthMm(), 2, 10, "每片长度", true);
        bounded(i.getPieceWidthMm(), 2, 10, "每片宽度", true);
        BigDecimal entered = i.getPurchaseQuantity();
        if (entered == null) entered = UnitCodes.ROLL.equals(purchase)
                ? (i.getRollCount() == null ? null : BigDecimal.valueOf(i.getRollCount())) : i.getQuantity();
        if (entered == null) throw new BusinessException("请填写采购数量");
        bounded(entered, 4, 8, "采购数量", false);
        ExactQuantity raw = exact(entered);
        if (PIECES.contains(purchase) || UnitCodes.ROLL.equals(purchase)) requireWhole(raw, "采购片数/卷数");
        if (UnitCodes.ROLL.equals(purchase) && !raw.isPositive()) throw new BusinessException("请补齐正整数卷数");
        ExactQuantity basic = toBase(raw, purchase, base, i);
        if (PIECES.contains(base)) requireWhole(basic, "库存片数");
        BigDecimal basicStored = basic.toBigDecimal(4, RoundingMode.HALF_UP);
        if (raw.isPositive() && basicStored.signum() == 0)
            throw new BusinessException("采购数量换算后小于库存精度，请核对计量依据");
        bounded(basicStored, 4, 8, "库存基本数量", false);
        ExactQuantity priced = fromBase(basic, base, price, i);
        if (UnitCodes.ROLL.equals(price)) {
            // 沿用已定口径：按卷报价不足整卷向上取整。
            priced = ExactQuantity.of(priced.ceilToInteger(), java.math.BigInteger.ONE);
        } else if (PIECES.contains(price)) requireWhole(priced, "计价片数");
        if (UnitCodes.ROLL.equals(purchase)) i.setRollCount(entered.intValueExact());
        else if (UnitCodes.ROLL.equals(price)) {
            try { i.setRollCount(priced.ceilToInteger().intValueExact()); }
            catch (ArithmeticException e) { throw new BusinessException("折算卷数超出范围"); }
        } else i.setRollCount(null);
        BigDecimal unitPrice = Objects.requireNonNullElse(i.getUnitPrice(), BigDecimal.ZERO);
        bounded(unitPrice, 4, 8, "采购单价", false);
        BigDecimal amount = priced.multiply(exact(unitPrice)).toBigDecimal(2, RoundingMode.HALF_UP);
        bounded(amount, 2, 10, "采购金额", false);
        i.setPurchaseQuantity(entered); i.setQuantity(basicStored);
        i.setPriceQuantity(priced.toBigDecimal(12, RoundingMode.HALF_UP));
        i.setUnitPrice(unitPrice.setScale(4, RoundingMode.HALF_UP)); i.setAmount(amount);
        i.setEquivalentUnitPrice(equivalent(i)); i.setPricingMessage(null);
    }
    public static BigDecimal tax(BigDecimal amount, BigDecimal rate) {
        if (rate == null) return null;
        bounded(rate, 2, 3, "税率", false);
        if (rate.compareTo(BigDecimal.valueOf(100)) > 0) throw new BusinessException("税率须在0至100之间");
        // 沿用原税额口径：行税额不提前舍入，合计由单头金额精度落库。
        return amount.multiply(rate).divide(BigDecimal.valueOf(100));
    }
    public static BigDecimal equivalent(PurchaseOrderItemDTO i) {
        String base = UnitCodes.normalize(i.getUnit());
        if (i.getQuantity() == null || i.getQuantity().signum() <= 0 || i.getAmount() == null) return null;
        ExactQuantity area;
        if (AREA.equals(base)) area = exact(i.getQuantity());
        else if (UnitCodes.M.equals(base) && i.getRollWidthMm() != null) area = exact(i.getQuantity()).multiply(width(i));
        else if (PIECES.contains(base) && i.getPieceLengthMm() != null && i.getPieceWidthMm() != null)
            area = exact(i.getQuantity()).multiply(pieceArea(i));
        else return null;
        return area.isPositive() ? exact(i.getAmount()).divide(area).toBigDecimal(2, RoundingMode.HALF_UP) : null;
    }
    private static boolean blank(String s) { return s == null || s.isBlank(); }
}
