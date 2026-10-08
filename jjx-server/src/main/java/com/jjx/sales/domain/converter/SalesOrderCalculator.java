package com.jjx.sales.domain.converter;

import com.jjx.sales.domain.entity.SalesOrder;
import com.jjx.sales.domain.dto.SalesOrderProductDTO;
import com.jjx.sales.domain.dto.SalesOrderEditDTO;
import com.jjx.sales.domain.vo.SalesOrderProductVO;
import com.jjx.common.exception.BusinessException;
import java.util.List;
import java.util.Objects;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 销售订单金额计算工具类
 */
public class SalesOrderCalculator {
    private SalesOrderCalculator() {
        /* This utility class should not be instantiated */
    }


    /** 明细单价为未税价；运费单列，不计入产品税基；优惠只在应付金额扣一次。 */
    public static void fillFromItems(SalesOrder order, List<SalesOrderProductDTO> items) {
        if (items == null || items.isEmpty()) throw new BusinessException("订单明细不能为空");
        BigDecimal subtotal = BigDecimal.ZERO;
        int quantity = 0;
        for (SalesOrderProductDTO item : items) {
            if (item.getQuantity() == null || item.getQuantity() <= 0 || item.getUnitPrice() == null
                    || item.getUnitPrice().signum() < 0) throw new BusinessException("订单明细数量或单价不正确");
            BigDecimal amount = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()))
                    .setScale(2, RoundingMode.HALF_UP);
            item.setAmount(amount);
            subtotal = subtotal.add(amount);
            quantity = Math.addExact(quantity, item.getQuantity());
        }
        BigDecimal rate = Objects.requireNonNullElse(order.getTaxRate(), BigDecimal.ZERO);
        BigDecimal freight = Objects.requireNonNullElse(order.getShippingFee(), BigDecimal.ZERO);
        if (rate.signum() < 0 || rate.compareTo(BigDecimal.valueOf(100)) > 0 || freight.signum() < 0)
            throw new BusinessException("税率或运费不正确");
        BigDecimal tax = subtotal.multiply(rate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal gross = subtotal.add(tax).add(freight).setScale(2, RoundingMode.HALF_UP);
        BigDecimal discount = order.getDiscountAmount();
        if (discount == null) discount = calculateDiscountAmount(gross, order.getDiscountRate());
        if (discount.signum() < 0 || discount.compareTo(gross) > 0) throw new BusinessException("折扣金额不能超过含税总金额");
        order.setTotalQuantity(quantity);
        order.setTaxAmount(tax);
        order.setTotalAmount(gross);
        order.setDiscountAmount(discount.setScale(2, RoundingMode.HALF_UP));
        fillOrderAmounts(order);
    }

    /** 旧单金额组成未知时允许非金额编辑，禁止用推算运费覆盖历史应收。 */
    public static void prepareEditAmounts(SalesOrder patch, SalesOrder existing, SalesOrderEditDTO dto,
                                          List<SalesOrderProductVO> oldItems) {
        if (dto.getShippingFee() == null) {
            if (!sameMoney(existing.getTotalAmount(), dto.getTotalAmount())
                    || !sameMoney(existing.getTaxRate(), dto.getTaxRate())
                    || !sameMoney(existing.getDiscountRate(), dto.getDiscountRate())
                    || (dto.getDiscountAmount() != null && !sameMoney(existing.getDiscountAmount(), dto.getDiscountAmount()))
                    || !Objects.equals(existing.getTotalQuantity(), dto.getTotalQuantity())
                    || oldItems == null || dto.getItems() == null
                    || !oldItems.stream().map(i -> priceKey(i.getProductId(), i.getProductCode(), i.getQuantity(), i.getUnitPrice(), i.getAmount())).sorted().toList()
                    .equals(dto.getItems().stream().map(i -> priceKey(i.getProductId(), i.getProductCode(), i.getQuantity(), i.getUnitPrice(), i.getAmount())).sorted().toList())) {
                throw new BusinessException("请先确认运费、折扣等金额组成，再修改订单金额或明细价格数量");
            }
            // 不写金额字段，保留旧单税额、优惠、应收与收款汇总。
            patch.setTotalAmount(null);
            patch.setTaxRate(null);
            patch.setDiscountRate(null);
            patch.setDiscountAmount(null);
            patch.setTotalQuantity(null);
            patch.setShippingFee(null);
            return;
        }
        patch.setPaidAmount(existing.getPaidAmount());
        fillFromItems(patch, dto.getItems());
        // 收款金额属于业务累计值，不通过订单编辑回写。
        patch.setPaidAmount(null);
        dto.setTotalAmount(patch.getTotalAmount());
        dto.setTotalQuantity(patch.getTotalQuantity());
        dto.setDiscountAmount(patch.getDiscountAmount());
    }

    private static boolean sameMoney(BigDecimal a, BigDecimal b) {
        return Objects.requireNonNullElse(a, BigDecimal.ZERO).compareTo(Objects.requireNonNullElse(b, BigDecimal.ZERO)) == 0;
    }

    private static String priceKey(Long productId, String code, Integer quantity, BigDecimal price, BigDecimal amount) {
        return productId + ":" + code + ":" + quantity + ":" +
                Objects.requireNonNullElse(price, BigDecimal.ZERO).stripTrailingZeros().toPlainString() + ":" +
                Objects.requireNonNullElse(amount, BigDecimal.ZERO).stripTrailingZeros().toPlainString();
    }

    /**
     * 从含税总金额中拆分税额。
     */
    public static BigDecimal calculateTaxAmount(BigDecimal totalAmount, BigDecimal taxRate) {
        if (totalAmount == null || taxRate == null) {
            return BigDecimal.ZERO;
        }
        if (taxRate.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return totalAmount.multiply(taxRate)
                .divide(BigDecimal.valueOf(100).add(taxRate), 2, RoundingMode.HALF_UP);
    }

    /**
     * 订单总金额已按含税金额保存，直接作为含税总金额使用。
     */
    public static BigDecimal calculateTotalAmountWithTax(BigDecimal totalAmount, BigDecimal taxAmount) {
        if (totalAmount == null) {
            return BigDecimal.ZERO;
        }
        return totalAmount;
    }

    /**
     * 计算折扣金额
     */
    public static BigDecimal calculateDiscountAmount(BigDecimal totalAmountWithTax, BigDecimal discountRate) {
        if (totalAmountWithTax == null || discountRate == null) {
            return BigDecimal.ZERO;
        }
        return totalAmountWithTax.multiply(discountRate).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 计算最终金额
     */
    public static BigDecimal calculateFinalAmount(BigDecimal totalAmountWithTax, BigDecimal discountAmount) {
        if (totalAmountWithTax == null) {
            return BigDecimal.ZERO;
        }
        if (discountAmount == null) {
            return totalAmountWithTax;
        }
        return totalAmountWithTax.subtract(discountAmount).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 填充订单金额字段
     */
    public static void fillOrderAmounts(SalesOrder order) {
        if (order == null || order.getTotalAmount() == null) {
            return;
        }

        // 设置默认值
        if (order.getTaxRate() == null) {
            order.setTaxRate(BigDecimal.ZERO);
        }
        if (order.getDiscountRate() == null) {
            order.setDiscountRate(BigDecimal.ZERO);
        }
        if (order.getPaidAmount() == null) {
            order.setPaidAmount(BigDecimal.ZERO);
        }

        // 1. 计算税额（调用方已传则保留——报价转订单场景继承报价税额）
        BigDecimal taxAmount = order.getTaxAmount();
        if (taxAmount == null) {
            taxAmount = calculateTaxAmount(order.getTotalAmount(), order.getTaxRate());
            order.setTaxAmount(taxAmount);
        }

        // 2. total_amount 本身已含税，不再叠加 tax_amount
        BigDecimal totalAmountWithTax = calculateTotalAmountWithTax(order.getTotalAmount(), taxAmount);
        order.setTotalAmountWithTax(totalAmountWithTax);

        // 3. 计算折扣金额（调用方已传则保留，否则按折扣率算）
        BigDecimal discountAmount = order.getDiscountAmount();
        if (discountAmount == null) {
            discountAmount = calculateDiscountAmount(totalAmountWithTax, order.getDiscountRate());
            order.setDiscountAmount(discountAmount);
        }

        // 4. 计算最终金额
        BigDecimal finalAmount = calculateFinalAmount(totalAmountWithTax, discountAmount);
        order.setFinalAmount(finalAmount);

        // 5. 计算未付金额
        BigDecimal unpaidAmount = finalAmount.subtract(order.getPaidAmount());
        order.setUnpaidAmount(unpaidAmount.max(BigDecimal.ZERO));

    }
}
