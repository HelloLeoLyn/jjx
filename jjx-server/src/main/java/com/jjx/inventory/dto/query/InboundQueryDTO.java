package com.jjx.inventory.dto.query;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 入库单查询参数DTO
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class InboundQueryDTO extends Page<Object> {

    private Long inboundId;
    private String inboundNo;
    private String inboundType;
    private Long warehouseId;
    private String sourceType;
    private String sourceNo;
    private String orderStatus;
    private String approveStatus;
    private LocalDate inboundDateStart;
    private LocalDate inboundDateEnd;
    private String createTimeStart;
    private String createTimeEnd;
    private String orderBy;
    private String orderDirection;
}
