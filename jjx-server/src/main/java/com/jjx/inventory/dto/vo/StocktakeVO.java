package com.jjx.inventory.dto.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 盘点单视图对象VO
 */
@Data
public class StocktakeVO {

    private Long stocktakeId;
    private String stocktakeNo;
    private String stocktakeType;
    private String stocktakeTypeName;
    private Long warehouseId;
    private String warehouseName;
    private String locationIds;
    private String materialIds;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime planStartTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime planEndTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime actualStartTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime actualEndTime;

    private Long stocktakerId;
    private String stocktakerName;
    private Long supervisorId;
    private String supervisorName;
    private BigDecimal totalSystemQuantity;
    private BigDecimal totalActualQuantity;
    private BigDecimal totalDiffQuantity;
    private BigDecimal totalDiffAmount;
    private String orderStatus;
    private String orderStatusName;
    private String approveStatus;
    private String approveStatusName;
    private Long approverId;
    private String approverName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime approveTime;

    private String approveRemark;
    private String remark;
    private String createBy;
    private String createByName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    private String updateBy;
    private String updateByName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    private List<StocktakeItemVO> items;
}

/**
 * 盘点单明细视图对象VO
 */
@Data
class StocktakeItemVO {
    private Long stocktakeItemId;
    private Long stocktakeId;
    private Long materialId;
    private String materialCode;
    private String materialName;
    private String specification;
    private String unit;
    private Long locationId;
    private String locationName;
    private String batchNo;
    private BigDecimal systemQuantity;
    private BigDecimal actualQuantity;
    private BigDecimal diffQuantity;
    private BigDecimal unitCost;
    private BigDecimal diffAmount;
    private String diffType;
    private String diffReason;
    private Integer sortOrder;
    private String remark;
}
