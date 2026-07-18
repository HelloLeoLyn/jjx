package com.jjx.inventory.dto.query;

import lombok.Data;

/**
 * 库存批次明细查询DTO
 */
@Data
public class StockItemQueryDTO {

    /** 当前页码 */
    private Integer current = 1;

    /** 每页条数 */
    private Integer pageSize = 10;

    /** 物料ID */
    private Long materialId;

    /** 物料编码 */
    private String materialCode;

    /** 物料名称 */
    private String materialName;

    /** 仓库ID */
    private Long warehouseId;

    /** 库位ID */
    private Long locationId;

    /** 批次号 */
    private String batchNo;

    /** 状态：0=未生效，1=生效 */
    private Integer status;

    /** 创建时间开始 */
    private String createTimeStart;

    /** 创建时间结束 */
    private String createTimeEnd;
}
