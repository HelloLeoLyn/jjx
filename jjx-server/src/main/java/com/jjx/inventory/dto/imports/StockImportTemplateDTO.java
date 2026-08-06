package com.jjx.inventory.dto.imports;

import com.jjx.common.annotation.ExcelColumn;
import lombok.Data;

/**
 * 库存导入模板DTO（DEV-672：库存导入模板统一由后端生成）
 * 对应前端 StockImportDialog 智能解析的表头
 */
@Data
public class StockImportTemplateDTO {

    /**
     * 物料名称（必填）
     */
    @ExcelColumn(value = "物料名称", order = 1, required = true, comment = "物料名称")
    private String materialName;

    /**
     * 规格型号
     */
    @ExcelColumn(value = "规格", order = 2, required = true, comment = "规格型号")
    private String specification;

    /**
     * 库存数量
     */
    @ExcelColumn(value = "库存数量", order = 3, required = true, comment = "当前库存数量")
    private String quantity;

    /**
     * 备注/说明
     */
    @ExcelColumn(value = "备注", order = 4, comment = "备注说明")
    private String remark;

    /**
     * 摆放/区域
     */
    @ExcelColumn(value = "摆放区域", order = 5, comment = "摆放/区域描述")
    private String locationDesc;

    /**
     * 供应商
     */
    @ExcelColumn(value = "供应商", order = 6, required = true, comment = "主要供应商名称")
    private String supplierName;

    /**
     * 仓库
     */
    @ExcelColumn(value = "仓库", order = 7, required = true, comment = "仓库名称")
    private String warehouseName;

    /**
     * 批次号
     */
    @ExcelColumn(value = "批次号", order = 8, required = true, comment = "批次号")
    private String batchNo;

    /**
     * 单位成本
     */
    @ExcelColumn(value = "单位成本", order = 9, required = true, comment = "单位成本")
    private String unitCost;

    /**
     * 生产日期
     */
    @ExcelColumn(value = "生产日期", order = 10, required = true, comment = "生产日期(yyyy-MM-dd)")
    private String productionDate;

    /**
     * 到期日期
     */
    @ExcelColumn(value = "到期日期", order = 11, required = true, comment = "到期日期(yyyy-MM-dd)")
    private String expiryDate;
}
