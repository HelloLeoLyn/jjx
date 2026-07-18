package com.jjx.inventory.dto.query;

import lombok.Data;

/**
 * 仓库查询参数DTO
 */
@Data
public class WarehouseQueryDTO {

    private Integer current = 1;
    private Integer size = 10;
    private Long warehouseId;
    private String warehouseCode;
    private String warehouseName;
    private String warehouseType;
    private String location;
    private String manager;
    private String contactPhone;
    private String status;
    private String createTimeStart;
    private String createTimeEnd;
}
