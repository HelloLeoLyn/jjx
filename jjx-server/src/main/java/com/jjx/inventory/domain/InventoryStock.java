package com.jjx.inventory.domain;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 库存汇总表实体类
 * 对应表：inventory_stock（按物料汇总）
 */
@Data
@TableName("inventory_stock")
public class InventoryStock implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 汇总记录ID */
    @TableId(type = IdType.AUTO)
    private Long stockId;

    /** 物料ID */
    private Long materialId;

    /** 物料编码（冗余） */
    private String materialCode;

    /** 物料名称（冗余） */
    private String materialName;

    /** 总库存数量 */
    private BigDecimal totalQuantity;

    /** 总预留数量 */
    private BigDecimal totalReserved;

    /** 可用数量（计算字段：total_quantity - total_reserved） */
    @TableField(exist = false)
    private BigDecimal availableQuantity;

    /** 当前最早有效期（来自最早批次的 expiry_date） */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate earliestExpiry;

    /** 最早批次所在库位ID */
    private Long locationId;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime lastUpdateTime;
}
