package com.jjx.inventory.dto.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 库存汇总视图对象VO
 * 按物料维度汇总
 */
@Data
public class StockVO {

    /** 汇总记录ID */
    private Long stockId;

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

    /** 总库存数量 */
    private BigDecimal totalQuantity;

    /** 总预留数量 */
    private BigDecimal totalReserved;

    /** 可用数量 */
    private BigDecimal availableQuantity;

    /** 总成本 */
    private BigDecimal totalCost;

    /** 平均单位成本 */
    private BigDecimal avgUnitCost;

    /** 最早有效期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate earliestExpiry;

    /** 最早批次所在库位ID */
    private Long locationId;

    /** 最早批次所在库位编码 */
    private String locationCode;

    /** 最早批次所在库位名称 */
    private String locationName;

    /** 物料安全库存 */
    private BigDecimal safeStock;

    /** 物料最高库存 */
    private BigDecimal maxStock;

    /** 是否低库存（低于安全库存） */
    private Boolean lowStock;

    /** 是否临期（30天内过期） */
    private Boolean expiring;

    /** 是否呆滞（180天未出库） */
    private Boolean obsolete;

    /** 距离过期天数 */
    private Long daysToExpiry;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
