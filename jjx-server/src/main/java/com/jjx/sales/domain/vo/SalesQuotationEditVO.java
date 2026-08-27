package com.jjx.sales.domain.vo;

import lombok.Data;

/** 报价单修改结果，同时为 @Log 提供字段级变更内容。 */
@Data
public class SalesQuotationEditVO {
    private int rows;
    private String detailMessage;
    private Integer bizStatus;
    private String traceId;
}
