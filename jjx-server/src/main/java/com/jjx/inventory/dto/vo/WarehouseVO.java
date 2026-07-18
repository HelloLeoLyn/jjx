package com.jjx.inventory.dto.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 仓库视图对象VO
 */
@Data
public class WarehouseVO {

    private Long warehouseId;
    private String warehouseCode;
    private String warehouseName;
    private String warehouseType;
    private String location;
    private String manager;
    private String contactPhone;
    private Integer sortOrder;
    private String status;
    private String createBy;
    private String createByName;
    private LocalDateTime createTime;
    private String updateBy;
    private String updateByName;
    private LocalDateTime updateTime;
}
