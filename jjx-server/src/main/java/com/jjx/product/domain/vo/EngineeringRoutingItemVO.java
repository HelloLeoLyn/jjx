package com.jjx.product.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 产品路线明细VO（包含工序完整信息）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EngineeringRoutingItemVO {

    // ==================== 路线明细字段 ====================
    private Long itemId;
    private Long routingId;

    // ==================== 组合字段 ====================
    private Long groupId;
    private Integer groupOrder;
    private String groupName;

    // ====================================================

    private Integer processOrder;
    private BigDecimal customLaborHours;
    private BigDecimal customMachineHours;
    private String customProcessParams;
    private String description;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    // ==================== 标准工序字段（直接平铺） ====================
    private Long processId;
    private String processCode;
    private String processName;
    private String processType;
    private String processTypeName;
    private String processCategory;
    private String processCategoryName;
    private BigDecimal standardLaborHours;
    private BigDecimal standardMachineHours;
    private String processParamTemplate;
    private String skillRequirement;
    private String equipmentType;
    private String qualityStandard;
    private Integer isEnabled;
    private String isEnabledName;
    private Integer displayOrder;
    private String icon;
}
