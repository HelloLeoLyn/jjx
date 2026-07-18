package com.jjx.inventory.dto.imports;

import com.jjx.common.annotation.ExcelColumn;
import lombok.Data;

/**
 * 库位导入DTO
 */
@Data
public class StorageLocationImportDTO {

    @ExcelColumn(value = "库位编码", order = 1, required = true, comment = "库位编码，如：A-01-01")
    private String locationCode;

    @ExcelColumn(value = "库位名称", order = 2, required = true, comment = "库位名称")
    private String locationName;

    @ExcelColumn(value = "库位类型", order = 3, comment = "库位类型：普通库位/冷冻库位/易燃库位/贵重库位")
    private String locationType;

    @ExcelColumn(value = "容量", order = 4, comment = "最大容量")
    private String capacity;

    @ExcelColumn(value = "宽度", order = 5, comment = "宽度(cm)")
    private String width;

    @ExcelColumn(value = "高度", order = 6, comment = "高度(cm)")
    private String height;

    @ExcelColumn(value = "深度", order = 7, comment = "深度(cm)")
    private String depth;

    @ExcelColumn(value = "排序", order = 8, comment = "排序序号")
    private String sortOrder;

    @ExcelColumn(value = "备注", order = 9, comment = "备注信息")
    private String remark;
}
