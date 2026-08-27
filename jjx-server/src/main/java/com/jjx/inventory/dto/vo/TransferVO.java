package com.jjx.inventory.dto.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 调拨单视图对象VO
 */
@Data
public class TransferVO {

    private Long transferId;
    private String transferNo;
    private String transferType;
    private Long fromWarehouseId;
    private String fromWarehouseName;
    private Long fromLocationId;
    private String fromLocationName;
    private Long toWarehouseId;
    private String toWarehouseName;
    private Long toLocationId;
    private String toLocationName;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate transferDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate expectedDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate actualDate;

    private BigDecimal totalQuantity;
    private BigDecimal totalAmount;
    private Integer orderStatus;
    private Integer approveStatus;
    private Long approverId;
    private String approverName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime approveTime;

    private String approveRemark;
    private String outOperator;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime outTime;

    private String inOperator;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime inTime;

    private String remark;
    private String createBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    private String updateBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    private List<TransferItemVO> items;
}

