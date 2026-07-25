package com.jjx.inventory.dto.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 出库单视图对象VO
 */
@Data
public class OutboundVO {

    private Long outboundId;
    private String outboundNo;
    private String outboundType;
    private String outboundTypeName;
    private Long warehouseId;
    private String warehouseName;
    private String sourceType;
    private String sourceTypeName;
    private Long sourceId;
    private String sourceNo;
    private Long customerId;
    private String customerName;
    private LocalDate outboundDate;
    private BigDecimal totalQuantity;
    private BigDecimal totalAmount;
    private String orderStatus;
    private String orderStatusName;
    private String approveStatus;
    private String approveStatusName;
    private String remark;
    private String createBy;
    private String createByName;
    private LocalDateTime createTime;
    private String updateBy;
    private String updateByName;
    private LocalDateTime updateTime;
    private List<OutboundItemVO> items;
}
