package com.jjx.inventory.dto.save;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 库位新增参数DTO
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class StorageLocationSaveDTO {
    private String areaCode;
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
