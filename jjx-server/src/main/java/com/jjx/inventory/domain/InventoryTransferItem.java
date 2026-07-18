package com.jjx.inventory.domain;

import com.baomidou.mybatisplus.annotation.*;
import com.jjx.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 调拨单明细表实体类
 * 对应表：inventory_transfer_item
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inventory_transfer_item")
public class InventoryTransferItem extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 明细ID */
    @TableId(type = IdType.AUTO)
    private Long itemId;

    /** 调拨单ID */
    private Long transferId;

    /** 物料ID */
    private Long materialId;

    /** 物料编码（冗余） */
    private String materialCode;

    /** 物料名称（冗余） */
    private String materialName;

    /** 规格型号 */
    private String specification;

    /** 单位 */
    private String unit;

    /** 调拨数量 */
    private BigDecimal quantity;

    /** 单位成本 */
    private BigDecimal unitCost;

    /** 金额 */
    private BigDecimal amount;

    /** 批次号 */
    private String batchNo;

    /** 实际出库库位 */
    private Long fromLocationId;

    /** 实际入库库位 */
    private Long toLocationId;

    /** 已出库数量 */
    private BigDecimal outQuantity;

    /** 已入库数量 */
    private BigDecimal inQuantity;

    /** 状态：pending待处理/partial部分完成/completed已完成 */
    private String status;

}
