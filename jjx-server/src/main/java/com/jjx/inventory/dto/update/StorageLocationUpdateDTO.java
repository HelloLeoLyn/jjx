package com.jjx.inventory.dto.update;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 库位更新参数DTO
 */
@Data
public class StorageLocationUpdateDTO {

    private Long locationId;
    private Long warehouseId;
    private Long parentId;
    private Boolean capacityEnabled;
    private String capacityUnit;
    private String remark;
    private String locationCode;
    private String locationName;
    private String locationType;
    private BigDecimal capacity;
    private BigDecimal width;
    private BigDecimal height;
    private BigDecimal depth;
    private Integer sortOrder;
    private String status;
}
