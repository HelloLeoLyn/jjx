package com.jjx.sales.domain.converter;

import com.jjx.sales.domain.entity.SalesOrder;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 销售订单金额计算工具类
 */
public class SalesOrderCalculator {
    private SalesOrderCalculator() {
        /* This utility class should not be instantiated */
    }


    /**
     * 计算税额
     */
    public static BigDecimal calculateTaxAmount(BigDecimal totalAmount, BigDecimal taxRate) {
        if (totalAmount == null || taxRate == null) {
            return BigDecimal.ZERO;
        }
        return totalAmount.multiply(taxRate).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 计算含税总金额
     */
    public static BigDecimal calculateTotalAmountWithTax(BigDecimal totalAmount, BigDecimal taxAmount) {
        if (totalAmount == null) {
            return BigDecimal.ZERO;
        }
        if (taxAmount == null) {
            return totalAmount;
        }
        return totalAmount.add(taxAmount);
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

        // 2. 计算含税总金额
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