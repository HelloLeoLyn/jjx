package com.jjx.sales.domain.vo;

import lombok.Data;

/**
 * 询价转报价返回值
 */
@Data
public class InquiryToQuotationVO {
    private Long quotationId;
    private String traceId;
}
