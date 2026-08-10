package com.jjx.inventory.domain.dto;

import lombok.Data;

import java.util.List;

/**
 * 批量处理预警DTO（采购计划确认后回写预警状态）
 */
@Data
public class AlertBatchProcessDTO {

    /** 预警ID列表 */
    private List<Long> alertIds;

    /** 关联采购订单号（写入处理备注） */
    private String relatedOrderNo;

    /** 处理备注 */
    private String remark;
}
