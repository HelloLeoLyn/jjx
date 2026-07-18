package com.jjx.inventory.dto.query;

import lombok.Data;

/**
 * 库存导入校验请求DTO
 * 用于校验物料是否存在，并根据摆放/区域解析仓库和库位
 */
@Data
public class StockCheckDTO {
    /** 物料名称 */
    private String materialName;

    /** 规格型号 */
    private String specification;

    /** 供应商名称 */
    private String supplierName;

    /** 摆放/区域描述（如 "A区 卡板 1"、"F区架一，二层-2"） */
    private String locationDesc;

    /** 仓库id */
    private Long warehouseId;
}
