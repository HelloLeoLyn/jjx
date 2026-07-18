package com.jjx.inventory.dto.query;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 盘点单查询参数DTO
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class StocktakeQueryDTO extends Page<Object> {

    private Long stocktakeId;
    private String stocktakeNo;
    private String stocktakeType;
    private Long warehouseId;
    private String orderStatus;
    private String approveStatus;
    private LocalDateTime planStartTimeStart;
    private LocalDateTime planStartTimeEnd;
    private LocalDateTime actualStartTimeStart;
    private LocalDateTime actualStartTimeEnd;
    private String createTimeStart;
    private String createTimeEnd;
    private String orderBy;
    private String orderDirection;
}
