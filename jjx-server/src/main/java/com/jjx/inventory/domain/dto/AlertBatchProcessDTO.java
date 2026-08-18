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

    /** 物料ID列表（2026-08-18：按物料回写该物料全部未处理预警，补手动添加行/低库存复燃场景） */
    private List<Long> materialIds;

    /** 关联采购订单号（写入处理备注） */
    private String relatedOrderNo;

    /** 处理备注 */
    private String remark;
}
