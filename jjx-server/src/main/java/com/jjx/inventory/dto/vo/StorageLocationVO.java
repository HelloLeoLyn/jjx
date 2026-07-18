package com.jjx.inventory.dto.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库位视图对象VO
 */
@Data
public class StorageLocationVO {

    private Long locationId;
    private Long warehouseId;
    private String warehouseCode;
    private String warehouseName;
    private String locationCode;
    private String locationName;
    private String locationType;
    private BigDecimal capacity;
    private BigDecimal usedCapacity;
    private BigDecimal width;
    private BigDecimal height;
    private BigDecimal depth;
    private Integer sortOrder;
    private String status;
    private String createBy;
    private String createByName;
    private LocalDateTime createTime;
    private String updateBy;
    private String updateByName;
    private LocalDateTime updateTime;
}
