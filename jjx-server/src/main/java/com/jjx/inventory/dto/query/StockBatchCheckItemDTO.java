package com.jjx.inventory.dto.query;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 库存批量校验请求项（DEV-697：模式③批量校验导入）
 * 对应前端导入表格的每一行
 */
@Data
public class StockBatchCheckItemDTO {

    /** 行号（1-based，用于前端定位错误行） */
    private Integer rowIndex;

    /** 物料名称 */
    private String materialName;

    /** 规格型号 */
    private String specification;

    /** 库存数量 */
    private BigDecimal quantity;

    /** 备注/说明 */
    private String remark;

    /** 摆放/区域描述 */
    private String locationDesc;

    /** 供应商名称 */
    private String supplierName;

    /** 仓库ID（可选，前端选了仓库后传） */
    private Long warehouseId;

    /** 仓库名称 */
    private String warehouseName;
}
