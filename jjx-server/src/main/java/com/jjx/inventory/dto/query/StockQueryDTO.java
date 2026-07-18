package com.jjx.inventory.dto.query;

import com.jjx.common.core.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 库存查询参数DTO
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class StockQueryDTO extends PageQuery {

    /**
     * 库存ID
     */
    private Long stockId;

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
     * 库位ID
     */
    private Long locationId;

    /**
     * 批次号
     */
    private String batchNo;

    /**
     * 库存状态
     */
    private String stockStatus;

    /**
     * 是否只查询有库存的记录
     */
    private Boolean onlyHasStock;

    /**
     * 最小库存数量
     */
    private BigDecimal minQuantity;

    /**
     * 最大库存数量
     */
    private BigDecimal maxQuantity;

    /**
     * 是否临期（true: 查询临期库存）
     */
    private Boolean expiring;

    /**
     * 是否呆滞（true: 查询呆滞库存）
     */
    private Boolean obsolete;

    /**
     * 是否低库存（true: 查询低于安全库存）
     */
    private Boolean lowStock;

    /**
     * 创建时间开始
     */
    private String createTimeStart;

    /**
     * 创建时间结束
     */
    private String createTimeEnd;

    /**
     * 最后入库时间开始
     */
    private String lastInboundTimeStart;

    /**
     * 最后入库时间结束
     */
    private String lastInboundTimeEnd;

    /**
     * 最后出库时间开始
     */
    private String lastOutboundTimeStart;

    /**
     * 最后出库时间结束
     */
    private String lastOutboundTimeEnd;

    /**
     * 排序字段
     */
    private String orderByColumn;

    /**
     * 排序方向
     */
    private String orderDirection;
}
