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
 * 库存批次明细表实体类
 * 对应表：inventory_stock_item
 */
@Data
@TableName("inventory_stock_item")
public class InventoryStockItem implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 明细ID */
    @TableId(type = IdType.AUTO)
    private Long itemId;

    /** 统一库存物品ID */
    private Long inventoryItemId;

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

    /** 批次号 */
    private String batchNo;
    private Long iqcBatchId;

    /** 物理卷身份，与供应商/检验批号独立；非卷材为空。 */
    private String rollNo;
    /** 原始卷宽(mm)，数量始终以㎡记账。 */
    private BigDecimal rollWidthMm;
    /** 首次实收原始卷长(m)，退回不覆盖此事实。 */
    private BigDecimal originalRollLengthM;
    /** 原始入库明细，卷材跨位置保持来源。 */
    private Long sourceInboundItemId;

    /** 生产日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate productionDate;

    /** 有效期至 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate expiryDate;

    /** 该批次数量 */
    private BigDecimal quantity;

    /** 该批次预留数量 */
    private BigDecimal reservedQuantity;

    /** 该批次单位成本 */
    private BigDecimal unitCost;

    /** 状态：0=未生效，1=生效 */
    private Integer status;

    /** 最后入库时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastInboundTime;

    /** 最后出库时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastOutboundTime;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
