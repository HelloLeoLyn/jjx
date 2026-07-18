package com.jjx.inventory.dto.query;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 库存导入请求DTO
 * 对应前端导入表格的每一行数据
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class StockImportDTO {

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

    /** 物料编码（校验后自动填充） */
    private String materialCode;

    /** 仓库ID */
    private Long warehouseId;

    /** 仓库名称 */
    private String warehouseName;

    /** 库位编码 */
    private String locationCode;

    /** 批次号 */
    private String batchNo;

    /** 单位成本 */
    private BigDecimal unitCost;

    /** 生产日期 */
    private String productionDate;

    /** 到期日期 */
    private String expiryDate;

    /** 供应商 */
    private String supplierName;
}
