package com.jjx.inventory.measurement;

import java.math.BigDecimal;

/** 完整可冻结依据；价格数量保留12位小数，货币及库存仅在最终业务落库时舍入。 */
public record MeasurementResult(String physicalUnit, String priceUnit, MeasurementPolicy.Mode mode,
                                BigDecimal physicalQuantity, BigDecimal priceQuantity,
                                String formula, MeasurementInput actual) { }
