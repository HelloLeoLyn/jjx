package com.jjx.product.domain.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/** 只允许维护产品参考售价与标准成本，原值用于检测并发调价。 */
@Data
public class ProductPriceUpdateDTO {
    @NotNull(message = "请填写基础售价")
    @DecimalMin(value = "0", message = "基础售价不能为负数")
    @Digits(integer = 10, fraction = 2, message = "基础售价最多10位整数和2位小数")
    private BigDecimal basePrice;

    @NotNull(message = "请填写标准成本")
    @DecimalMin(value = "0", message = "标准成本不能为负数")
    @Digits(integer = 10, fraction = 2, message = "标准成本最多10位整数和2位小数")
    private BigDecimal costPrice;

    @Digits(integer = 10, fraction = 2)
    private BigDecimal expectedBasePrice;
    @Digits(integer = 10, fraction = 2)
    private BigDecimal expectedCostPrice;
}
