package com.jjx.sales.domain.dto;

import lombok.Data;

/**
 * 样品转量产时补充的标准订单信息。
 */
@Data
public class SampleConvertExtrasDTO {

    private String paymentTerms;

    private String deliveryTerms;

    private String deliveryAddress;

    private String contactPerson;

    private String contactPhone;
}
