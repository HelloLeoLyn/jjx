package com.jjx.inventory.domain;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单材料预占表实体（094定稿：确认前手动预占原料，解决"已审核未确认订单不占料"盲区）
 */
@Data
@TableName("order_material_reserve")
public class OrderMaterialReserve implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 预占记录ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 销售订单ID */
    private Long orderId;

    /** 销售订单号 */
    private String orderNo;

    /** 物料ID */
    private Long materialId;

    /** 物料编码 */
    private String materialCode;

    /** 物料名称 */
    private String materialName;

    /** 预占数量 */
    private BigDecimal reserveQuantity;

    /** 状态：0占用中 1已释放 2已转正式预留 */
    private Integer status;

    /** 预占天数(1~7) */
    private Integer reserveDays;

    /** 预占时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime reserveTime;

    /** 到期时间(预占时间+天数) */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expireTime;

    /** 释放原因 */
    private String releaseReason;

    /** 释放人 */
    private String releaseBy;

    /** 释放时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime releaseTime;

    /** 创建人 */
    private String createBy;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新人 */
    private String updateBy;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
