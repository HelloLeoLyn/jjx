package com.jjx.inventory.dto.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存流水VO
 */
@Data
public class TransactionVO {

    /**
     * 流水ID
     */
    private Long transactionId;

    /**
     * 物料ID
     */
    private Long materialId;

    /**
     * 物料编码
     */
    private String materialCode;

    /**
     * 物料名称
     */
    private String materialName;

    /**
     * 仓库ID
     */
    private Long warehouseId;

    /**
     * 仓库名称
     */
    private String warehouseName;

    /**
     * 库位ID
     */
    private Long locationId;

    /**
     * 库位名称
     */
    private String locationName;

    /**
     * 交易类型：inbound入库/outbound出库/transfer_in调拨入库/transfer_out调拨出库/adjust盘盈盘亏
     */
    private String transactionType;

    /**
     * 交易类型名称
     */
    private String transactionTypeName;

    /**
     * 来源类型：purchase_order/work_order/sales_order/stocktake
     */
    private String sourceType;

    /**
     * 来源类型名称
     */
    private String sourceTypeName;

    /**
     * 来源单据ID
     */
    private Long sourceId;

    /**
     * 来源单号
     */
    private String sourceNo;

    /**
     * 批次号
     */
    private String batchNo;

    /**
     * 变动数量（正数增加，负数减少）
     */
    private BigDecimal quantity;

    /**
     * 变动前数量
     */
    private BigDecimal beforeQuantity;

    /**
     * 变动后数量
     */
    private BigDecimal afterQuantity;

    /**
     * 单位成本
     */
    private BigDecimal unitCost;

    /**
     * 变动金额
     */
    private BigDecimal amount;

    /**
     * 交易时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime transactionTime;

    /**
     * 操作人ID
     */
    private Long operatorId;

    /**
     * 操作人姓名
     */
    private String operatorName;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /**
     * 备注
     */
    private String remark;
}
