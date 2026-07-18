package com.jjx.inventory.dto.query;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 调拨单查询参数DTO
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TransferQueryDTO extends Page<Object> {

    private Long transferId;
    private String transferNo;
    private String transferType;
    private Long fromWarehouseId;
    private Long toWarehouseId;
    private String orderStatus;
    private String approveStatus;
    private LocalDate transferDateStart;
    private LocalDate transferDateEnd;
    private String createTimeStart;
    private String createTimeEnd;
    private String orderBy;
    private String orderDirection;
}
