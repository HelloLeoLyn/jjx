package com.jjx.product.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 产品标准工序实体类
 */
@Data
@TableName("product_standard_process")
public class ProductStandardProcess {
    
    @TableId(type = IdType.AUTO)
    private Long processId;
    
    /**
     * 工序编码
     */
    private String processCode;
    
    /**
     * 工序名称
     */
    private String processName;
    
    /**
     * 工序类型
     */
    private String processType;
    
    /**
     * 工序类别
     */
    private String processCategory;
    
    /**
     * 标准人工工时
     */
    private BigDecimal standardLaborHours;
    
    /**
     * 标准机器工时
     */
    private BigDecimal standardMachineHours;
    
    /**
     * 工艺参数模板
     */
    private String processParamTemplate;
    
    /**
     * 技能要求
     */
    private String skillRequirement;
    
    /**
     * 设备类型
     */
    private String equipmentType;
    
    /**
     * 质量标准
     */
    private String qualityStandard;
    
    /**
     * 工序说明
     */
    private String description;

    /**
     * 图标
     */
    private String icon;
    
    /**
     * 是否启用
     */
    private Integer isEnabled;
    
    /**
     * 显示顺序
     */
    private Integer displayOrder;
    
    /**
     * 创建者
     */
    @TableField(fill = FieldFill.INSERT)
    private String createBy;
    
    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    
    /**
     * 更新者
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;
    
    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    
    // ==================== 非数据库字段 ====================
    
    /**
     * 工序类型名称
     */
    @TableField(exist = false)
    private String processTypeName;
    
    /**
     * 工序类别名称
     */
    @TableField(exist = false)
    private String processCategoryName;
    
    /**
     * 是否启用名称
     */
    @TableField(exist = false)
    private String isEnabledName;
}