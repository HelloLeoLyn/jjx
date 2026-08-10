package com.jjx.engineering.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.jjx.product.domain.entity.ProductStandardProcess;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 产品路线明细实体类
 */
@Data
@TableName("engineering_routing_item")
public class EngineeringRoutingItem {

    @TableId(value = "detail_id", type = IdType.AUTO)
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

    /** 标准工价（元/工时，059人工成本核算） */
    private BigDecimal standardWage;

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

    /**
     * 下标数字（带下标工序的下标值，如4显示为④）
     */
    private Integer indexNumber;

    /**
     * 前置依赖标识（如 PANEL_4=面板④）
     */
    private String precondition;

    /**
     * 前置依赖显示名（如：面板④ 面板冲型）
     */
    private String preconditionDisplay;

    /**
     * 可选工序：0-必做,1-可选
     */
    private Integer isOptional;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    // ==================== 非数据库字段 ====================

    /**
     * 标准工序信息
     */
    @TableField(exist = false)
    private ProductStandardProcess standardProcess;

    /**
     * 工序名称
     */
    @TableField(exist = false)
    private String processName;

    /**
     * 工序类型
     */
    @TableField(exist = false)
    private String processType;
}
