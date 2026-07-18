package com.jjx.inventory.domain;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.jjx.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存流水表实体类
 * 对应表：inventory_transaction
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("inventory_transaction")
public class InventoryTransaction extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 流水ID */
    @TableId(type = IdType.AUTO)
    private Long transactionId;

    /** 物料ID */
    private Long materialId;

    /** 物料编码（冗余） */
    private String materialCode;

    /** 物料名称（冗余） */
    private String materialName;

    /** 仓库ID */
    private Long warehouseId;

    /** 库位ID */
    private Long locationId;

    /** 交易类型：inbound入库/outbound出库/transfer_in调拨入库/transfer_out调拨出库/adjust盘盈盘亏 */
    private String transactionType;

    /** 来源类型：purchase_order/work_order/sales_order/stocktake */
    private String sourceType;

    /** 来源单据ID */
    private Long sourceId;

    /** 来源单号 */
    private String sourceNo;

    /** 批次号 */
    private String batchNo;

    /** 变动数量（正数增加，负数减少） */
    private BigDecimal quantity;

    /** 变动前数量 */
    private BigDecimal beforeQuantity;

    /** 变动后数量 */
    private BigDecimal afterQuantity;

    /** 单位成本 */
    private BigDecimal unitCost;

    /** 变动金额 */
    private BigDecimal amount;

    /** 交易时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime transactionTime;

    /** 操作人ID */
    private Long operatorId;

    /** 操作人姓名 */
    private String operatorName;

}
