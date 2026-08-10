package com.jjx.inventory.domain;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 产品库存汇总表实体类
 * 对应表：product_stock（产品维度独立记账，与物料库存各自独立）
 * 概念红线：产品=产品档案（BOM/工艺路线），成品物料≠产品；
 * 完工入库入的是产品库存，销售出库扣的是产品库存，产品看库存直接查本表
 */
@Data
@TableName("product_stock")
public class ProductStock implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 产品库存记录ID */
    @TableId(type = IdType.AUTO)
    private Long stockId;

    /** 产品ID */
    private Long productId;

    /** 产品编码（冗余） */
    private String productCode;

    /** 产品名称（冗余） */
    private String productName;

    /** 总库存数量 */
    private BigDecimal totalQuantity;

    /** 预留数量 */
    private BigDecimal totalReserved;

    /** 可用数量（DB 生成列：total_quantity - total_reserved，只读不写） */
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private BigDecimal availableQuantity;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime lastUpdateTime;
}
