package com.jjx.inventory.dto.query;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 出库单查询参数DTO
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OutboundQueryDTO extends Page<Object> {

    private Long outboundId;
    private String outboundNo;
    private String outboundType;
    private Long warehouseId;
    private String sourceType;
    private String sourceTypeNe; // DEV-659：排除指定来源类型（出库单入口排除领料单 work_order）
    private String sourceNo;
    private String orderStatus;
    private String approveStatus;
    private LocalDate outboundDateStart;
    private LocalDate outboundDateEnd;
    private String createTimeStart;
    private String createTimeEnd;
    private String orderBy;
    private String orderDirection;
}
