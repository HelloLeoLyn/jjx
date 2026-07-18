package com.jjx.purchase.domain.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderItemDTO {
    private Integer itemNo;           // 项次
    private String materialName;      // 品名
    private String specification;     // 规格
    private String unit;              // 单位
    private BigDecimal quantity;      // 数量
    private BigDecimal unitPrice;     // 单价
    private BigDecimal amount;        // 金额
    private String remark;            // 备注
}
