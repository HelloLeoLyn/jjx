package com.jjx.inventory.dto.vo;

import lombok.Data;

/**
 * 库存导入校验结果VO
 * 包含物料信息、仓库信息、库位信息
 */
@Data
public class StockCheckVO {
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

    /** 仓库名称 */
    private String warehouseName;

    /** 库位编码 */
    private String locationCode;

    /** 库位名称 */
    private String locationName;
}
