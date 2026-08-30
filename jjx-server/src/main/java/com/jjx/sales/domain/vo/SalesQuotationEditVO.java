package com.jjx.sales.domain.vo;

import lombok.Data;

/** 报价单修改结果，同时为 @Log 提供字段级变更内容。 */
@Data
public class SalesQuotationEditVO {
    private int rows;
    private String detailMessage;
    /** 修改完成后报价单在库中的真实状态文案（供 @Log bizStatus 取值） */
    private String bizStatus;
    private String traceId;
}
