package com.jjx.inventory.dto.query;

import lombok.Data;

/**
 * 库位查询参数DTO
 */
@Data
public class StorageLocationQueryDTO {

    private Integer current = 1;
    private Integer size = 10;
    public void setPageNum(Integer value) { if(value!=null && value>0)current=value; }
    public void setPageSize(Integer value) { if(value!=null && value>0 && value<=500)size=value; }
    private Long locationId;
    private Long warehouseId;
    private String locationCode;
    private String locationName;
    private String locationType;
    private String status;
    private String createTimeStart;
    private String createTimeEnd;
}
