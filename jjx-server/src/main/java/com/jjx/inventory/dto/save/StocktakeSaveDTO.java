package com.jjx.inventory.dto.save;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 盘点单新增参数DTO
 */
@Data
public class StocktakeSaveDTO {

    private String stocktakeType;
    private Long warehouseId;
    private List<Long> locationIds;
    private List<Long> materialIds;
    private LocalDateTime planStartTime;
    private LocalDateTime planEndTime;
    private Long stocktakerId;
    private String stocktakerName;
    private Long supervisorId;
    private String supervisorName;
    private String remark;
}
