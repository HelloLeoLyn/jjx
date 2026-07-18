package com.jjx.inventory.dto.update;

import lombok.Data;

/**
 * 仓库更新参数DTO
 */
@Data
public class WarehouseUpdateDTO {

    private Long warehouseId;
    private String warehouseCode;
    private String warehouseName;
    private String warehouseType;
    private String location;
    private String manager;
    private String contactPhone;
    private Integer sortOrder;
    private String status;
}
