package com.jjx.inventory.dto.imports;

import com.jjx.common.annotation.ExcelColumn;
import lombok.Data;

/**
 * 物料导入DTO
 */
@Data
public class MaterialImportDTO {

    @ExcelColumn(value = "材料", order = 1, required = true, comment = "物料名称")
    private String materialName;

    @ExcelColumn(value = "规格", order = 2, comment = "规格型号")
    private String specification;

    @ExcelColumn(value = "供应商", order = 3, comment = "主要供应商名称")
    private String supplierName;

    @ExcelColumn(value = "备注", order = 4, comment = "备注信息")
    private String remark;

    @ExcelColumn(value = "机种", order = 5,comment = "机种名称")
    private String materialNameEn;

    @ExcelColumn(value = "数量", order = 5,comment = "当前库存")
    private String currentStock;

    @ExcelColumn(value = "单位", order = 6,comment = "单位")
    private String unit;

    @ExcelColumn(value = "项目", order = 6,comment = "项目")
    private String processGroup;

    @ExcelColumn(value = "材料类型", order = 6,comment = "材料类型")
    private String materialType;
}
