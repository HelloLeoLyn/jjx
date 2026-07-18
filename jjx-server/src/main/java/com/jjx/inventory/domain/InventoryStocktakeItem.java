package com.jjx.inventory.domain;

import com.baomidou.mybatisplus.annotation.*;
import com.jjx.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 盘点明细表实体类
 * 对应表：inventory_stocktake_item
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inventory_stocktake_item")
public class InventoryStocktakeItem extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 明细ID */
    @TableId(type = IdType.AUTO)
    private Long itemId;

    /** 盘点单ID */
    private Long stocktakeId;

    /** 物料ID */
    private Long materialId;

    /** 物料编码（冗余） */
    private String materialCode;

    /** 物料名称（冗余） */
    private String materialName;

    /** 批次号 */
    private String batchNo;

    /** 库位ID */
    private Long locationId;

    /** 系统账面数量 */
    private BigDecimal systemQuantity;

    /** 实际盘点数量 */
    private BigDecimal actualQuantity;

    /** 差异数量（计算字段） */
    @TableField(exist = false)
    private BigDecimal diffQuantity;

    /** 单位成本 */
    private BigDecimal unitCost;

    /** 差异金额（计算字段） */
    @TableField(exist = false)
    private BigDecimal diffAmount;

    /** 调整状态：pending待处理/processed已处理/skipped已跳过 */
    private String adjustStatus;

    /** 生成的调整单ID */
    private Long adjustOrderId;

    /** 差异原因 */
    private String reason;

}
