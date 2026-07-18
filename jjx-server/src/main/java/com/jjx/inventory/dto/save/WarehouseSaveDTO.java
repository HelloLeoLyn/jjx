package com.jjx.inventory.dto.save;

import lombok.Data;

/**
 * 仓库新增参数DTO
 */
@Data
public class WarehouseSaveDTO {

    private String warehouseCode;
    private String warehouseName;
    private String warehouseType;
    private String location;
    private String manager;
    private String contactPhone;
    private Integer sortOrder;
    private String status;
}
