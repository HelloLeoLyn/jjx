package com.jjx.inventory.domain;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.jjx.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 入库单明细表实体类
 * 对应表：inventory_inbound_item
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inventory_inbound_item")
public class InventoryInboundItem extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 明细ID */
    @TableId(type = IdType.AUTO)
    private Long itemId;

    /** 入库单ID */
    private Long inboundId;

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

    /** 入库数量 */
    private BigDecimal quantity;

    /** 单价 */
    private BigDecimal unitPrice;

    /** 金额 */
    private BigDecimal amount;

    /** 批次号 */
    private String batchNo;

    /** 生产日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate productionDate;

    /** 有效期至 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate expiryDate;

    /** 实际存放库位 */
    private Long locationId;

    /** 合格数量 */
    private BigDecimal qualifiedQuantity;

    /** 不合格数量 */
    private BigDecimal rejectedQuantity;

    /** 不合格原因 */
    private String rejectReason;

    /** 排序 */
    private Integer sortOrder;

    /** 备注 */
    private String remark;

}
