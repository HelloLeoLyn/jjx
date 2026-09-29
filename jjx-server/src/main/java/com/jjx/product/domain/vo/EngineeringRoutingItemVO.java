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
    /** 2026-09-05 父子结构：父行 parentId=null；组合作业项挂父行 */
    private Long parentId;

    /** 2026-09-05 父子结构：父行的组合作业项（回显组树） */
    private java.util.List<EngineeringRoutingItemVO> children;

    // ====================================================

    private Integer processOrder;
    /** 组（workflow）序号：同组内 processOrder 从 1 递增（历史档案按 面板/上线/下线 分组，2026-09-29 用户拍板 B） */
    private Integer workflowSeq;
    private BigDecimal customLaborHours;
    private BigDecimal customMachineHours;
    private String customProcessParams;
    private String description;
    private String workInstruction;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    // ==================== 标准工序字段（直接平铺） ====================
    private Long processId;
    /** 大类：ASSEMBLY冲型组装/PRINT印刷（2026-08-12） */
    private String majorCategory;
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

    // ==================== 下标/依赖/可选（批次1新增） ====================
    private Integer indexNumber;
    private String precondition;
    private String preconditionDisplay;
    private Integer isOptional;
    /** 标准工序是否带下标（平铺自 standard_process.has_index） */
    private Integer hasIndex;
    /** 标准工序是否常规携带实例作业说明 */
    private Integer hasWorkInstruction;
}
