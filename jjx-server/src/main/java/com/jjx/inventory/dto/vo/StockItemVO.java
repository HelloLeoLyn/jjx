package com.jjx.inventory.dto.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 库存批次明细视图对象VO
 */
@Data
public class StockItemVO {

    /** 明细ID */
    private Long itemId;
    private Long inventoryItemId;

    /** 物料ID */
    private Long materialId;

    /** 物料编码 */
    private String materialCode;

    /** 物料名称 */
    private String materialName;

    /** 规格型号 */
    private String specification;

    /** 单位 */
    private String unit;

    /** 仓库ID */
    private Long warehouseId;

    /** 仓库编码 */
    private String warehouseCode;

    /** 仓库名称 */
    private String warehouseName;

    /** 库位ID */
    private Long locationId;

    /** 库位编码 */
    private String locationCode;

    /** 库位名称 */
    private String locationName;

    /** 批次号 */
    private String batchNo;
    private Long iqcBatchId;

    private java.math.BigDecimal receivedQuantity;
    private java.math.BigDecimal issuedQuantity;
    private java.math.BigDecimal balanceQuantity;
    private Long lastOutboundItemId;
    private String rollNo;
    private BigDecimal rollWidthMm;
    private BigDecimal originalRollLengthM;
    private Long sourceInboundItemId;
    /** 展示派生值：当前㎡结存÷卷宽，不作为余额落库。 */
    private BigDecimal remainingRollLengthM;
    private Boolean partialRoll;

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

    /** 可用数量 */
    private BigDecimal availableQuantity;

    /** 单位成本 */
    private BigDecimal unitCost;

    /** 状态：0=未生效，1=生效 */
    private Integer status;

    /** 状态名称 */
    private String statusName;

    /** 最后入库时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastInboundTime;

    /** 最后出库时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastOutboundTime;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
