package com.jjx.inventory.dto.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 盘点单明细视图对象VO
 */
@Data
public class StocktakeItemVO {
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

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime stocktakeTime;

    private String stocktakeBy;
}
