package com.jjx.inventory.domain;

import com.baomidou.mybatisplus.annotation.*;
import com.jjx.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 出库单明细表实体类
 * 对应表：inventory_outbound_item
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inventory_outbound_item")
public class InventoryOutboundItem extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 明细ID */
    @TableId(type = IdType.AUTO)
    private Long itemId;

    /** 出库单ID */
    private Long outboundId;

    /** 统一库存物品ID */
    private Long inventoryItemId;

    /** 来源发货明细，合并发货按此释放所属订单预留并核验原始量。 */
    private Long deliveryItemId;

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

    /** 出库数量 */
    private BigDecimal quantity;

    /** 出库单价 */
    private BigDecimal unitPrice;

    /** 金额 */
    private BigDecimal amount;

    /** 批次号 */
    private String batchNo;

    /** 出库库位 */
    private Long locationId;

    /** 排序 */
    private Integer sortOrder;

    /** 本次领料对应的BOM明细（替代料也指向被替代项） */
    private Long sourceBomItemId;

    /** 实际领料量折抵的原BOM需求量 */
    private BigDecimal coverageQuantity;

    /** 被替代的原BOM物料ID；原物料领料为空 */
    private Long substituteOfMaterialId;

    /** 本次替代换算系数及损耗率快照 */
    private BigDecimal allocationRatio;
    private BigDecimal allocationLossRate;

    /** 本次替代依据 */
    private String allocationReason;

}
