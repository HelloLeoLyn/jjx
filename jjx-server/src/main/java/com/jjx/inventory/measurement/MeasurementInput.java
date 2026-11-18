package com.jjx.inventory.measurement;

import java.math.BigDecimal;

/** 实际参数优先于档案默认。长(mm)用于片料，卷长(m)用于逐卷；实测重量为本次数量总净重。 */
public record MeasurementInput(BigDecimal physicalQuantity, BigDecimal widthMm, BigDecimal lengthMm,
                               BigDecimal rollLengthM, BigDecimal unitWeightG, BigDecimal grammage,
                               BigDecimal actualWeightKg) { }
