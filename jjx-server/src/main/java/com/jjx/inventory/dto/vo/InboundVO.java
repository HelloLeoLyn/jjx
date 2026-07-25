package com.jjx.inventory.dto.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 入库单视图对象VO
 */
@Data
public class InboundVO {

    private Long inboundId;
    private String inboundNo;
    private String inboundType;
    private Long warehouseId;
    private String warehouseName;
    private Long locationId;
    private String locationName;
    private String sourceType;
    private Long sourceId;
    private String sourceNo;
    private Long supplierId;
    private String supplierName;
    private LocalDate inboundDate;
    private Long inspectorId;
    private String inspectorName;
    private String inspectionResult;
    private String inspectionRemark;
    private BigDecimal totalQuantity;
    private BigDecimal totalAmount;
    private String orderStatus;
    private String approveStatus;
    private String remark;
    private String createBy;
    private String createByName;
    private LocalDateTime createTime;
    private String updateBy;
    private String updateByName;
    private LocalDateTime updateTime;
    private List<InboundItemVO> items;
}
