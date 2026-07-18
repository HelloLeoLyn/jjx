package com.jjx.inventory.dto.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 库存预警视图对象VO
 */
@Data
public class AlertVO {

    private Long alertId;
    private String alertType;
    private String alertTypeName;
    private String alertLevel;
    private String alertLevelName;
    private Long materialId;
    private String materialCode;
    private String materialName;
    private String specification;
    private String unit;
    private Long warehouseId;
    private String warehouseName;
    private Long locationId;
    private String locationName;
    private String batchNo;
    private BigDecimal currentStock;
    private BigDecimal safeStock;
    private BigDecimal maxStock;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate expiryDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate lastOutboundDate;

    private String alertMessage;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime alertTime;

    private String status;
    private String statusName;
    private String processedBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime processedTime;

    private String processRemark;
    private String suggestion;
    private String createBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    private String updateBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
