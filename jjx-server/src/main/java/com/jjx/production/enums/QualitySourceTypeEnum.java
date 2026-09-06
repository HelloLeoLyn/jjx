package com.jjx.production.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum QualitySourceTypeEnum {
    INBOUND("INBOUND", "采购入库"),
    PRODUCTION_ORDER("PRODUCTION_ORDER", "生产订单"),
    OPERATION_EXECUTION("OPERATION_EXECUTION", "工序执行"),
    OUTBOUND("OUTBOUND", "销售出库");

    private final String code;
    private final String label;
}
