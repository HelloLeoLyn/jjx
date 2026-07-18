package com.jjx.product.domain.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 产品路线明细 DTO
 */
@Data
public class ProductRoutingItemDTO {

    /**
     * 明细ID（新增时为 null，修改时有值）
     */
    private Long itemId;

    /**
     * 路线ID
     */
    private Long routingId;

    // ==================== 组合字段 ====================

    /**
     * 组合ID（同一组合的工序共享此ID，NULL表示独立工序）
     */
    private Long groupId;

    /**
     * 组合顺序
     */
    private Integer groupOrder;

    /**
     * 组合名称
     */
    private String groupName;

    // ================================================

    /**
     * 标准工序ID
     */
    private Long processId;

    /**
     * 工序顺序
     */
    private Integer processOrder;

    /**
     * 定制人工工时
     */
    private BigDecimal customLaborHours;

    /**
     * 定制机器工时
     */
    private BigDecimal customMachineHours;

    /**
     * 定制工艺参数
     */
    private String customProcessParams;

    /**
     * 工序说明
     */
    private String description;

    /**
     * 工序类别（字典表 process_category）
     */
    private String processCategory;
}
