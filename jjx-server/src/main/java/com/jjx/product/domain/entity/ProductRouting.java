package com.jjx.product.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.jjx.common.core.domain.BaseEntity;
import com.jjx.product.enums.ProductEnums;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.List;

/**
 * 产品工艺路线实体类
 */
@EqualsAndHashCode(callSuper = true)
@Data
@TableName("engineering_routing")
public class ProductRouting extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long routingId;

    /**
     * 路线编码
     */
    private String routingCode;

    /**
     * 路线名称
     */
    private String routingName;

    /**
     * 产品ID
     */
    private Long productId;

    /**
     * 产品编码
     */
    private String productCode;

    /**
     * 产品名称
     */
    private String productName;

    /**
     * 版本号
     */
    private String routingVersion;

    /**
     * 是否当前版本：0否 1是
     */
    private Integer isCurrent;

    /**
     * 审核状态
     */
    private Integer approveStatus;

    /**
     * 总人工工时
     */
    private BigDecimal totalLaborHours;

    /**
     * 总机器工时
     */
    private BigDecimal totalMachineHours;

    /**
     * 工序数量
     */
    private Integer processCount;

    /**
     * 路线说明
     */
    private String description;



    /**
     * 备注
     */
    private String remark;

    // ==================== 非数据库字段 ====================

    /**
     * 工序明细列表
     */
    @TableField(exist = false)
    private List<ProductRoutingItem> items;

    /**
     * 审核状态名称
     */
    @TableField(exist = false)
    private String approveStatusName;

    /**
     * 是否当前版本名称
     */
    @TableField(exist = false)
    private String isCurrentName;
}
