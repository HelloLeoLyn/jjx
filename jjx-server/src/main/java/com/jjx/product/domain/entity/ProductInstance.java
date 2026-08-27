package com.jjx.product.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 产品实例实体类
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("product_instance")
public class ProductInstance {

    /**
     * 实例ID
     */
    @TableId(type = IdType.AUTO)
    private Long instanceId;

    /**
     * 实例编码
     */
    private String instanceCode;

    /**
     * 产品ID
     */
    private Long productId;


    /**
     * 订单ID
     */
    private Long orderId;


    /**
     * 客户ID
     */
    private Long customerId;

    /**
     * 客户名称
     */
    private String customerName;


    /**
     * 数量
     */
    private Integer quantity;

//    /**
//     * 单价
//     */
//    private BigDecimal unitPrice;

//    /**
//     * 总金额
//     */
//    private BigDecimal totalAmount;

    /**
     * 状态：pending待生产/in_production生产中/completed已完成/delivered已交付
     */
    private Integer instanceStatus;

//    /**
//     * 生产开始时间
//     */
//    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
//    private LocalDateTime productionStartTime;
//
//    /**
//     * 生产完成时间
//     */
//    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
//    private LocalDateTime productionEndTime;
//
//    /**
//     * 交付时间
//     */
//    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
//    private LocalDateTime deliveryTime;

    /**
     * 创建者
     */
    @TableField(fill = FieldFill.INSERT)
    private String createBy;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
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
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    /**
     * 备注
     */
    private String remark;
}
