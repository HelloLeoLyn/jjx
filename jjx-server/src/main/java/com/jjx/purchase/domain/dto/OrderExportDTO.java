// dto/OrderExportDTO.java
package com.jjx.purchase.domain.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class OrderExportDTO {
    private String orderNo;           // 订单号码
    private String orderDate;         // 订货时间
    private String deliveryDate;      // 交货时间
    private String supplierName;      // 厂商名称
    private String supplierContact;   // 联系人
    private String supplierTel;       // 电话
    private String tradeType;         // 交易方式 RMB/monthly
    private List<OrderItemDTO> items; // 订单明细
    private BigDecimal totalAmount;   // 合计金额
}

