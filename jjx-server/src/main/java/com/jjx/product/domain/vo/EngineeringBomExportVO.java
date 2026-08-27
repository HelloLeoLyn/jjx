package com.jjx.product.domain.vo;

import com.jjx.common.annotation.ExcelColumn;
import lombok.Data;

import java.util.Date;

/**
 * BOM列表导出视图对象
 */
@Data
public class EngineeringBomExportVO {

    @ExcelColumn(value = "BOM编码", order = 1)
    private String bomCode;

    @ExcelColumn(value = "BOM版本", order = 2)
    private String bomVersion;

    @ExcelColumn(value = "产品编码", order = 3)
    private String productCode;

    @ExcelColumn(value = "产品名称", order = 4)
    private String productName;

    @ExcelColumn(value = "BOM类型", order = 5)
    private String bomTypeName;

    @ExcelColumn(value = "审核状态", order = 6)
    private String approveStatusName;

    @ExcelColumn(value = "是否当前版本", order = 7)
    private String isCurrentName;

    @ExcelColumn(value = "生效日期", order = 8)
    private Date effectiveDate;

    @ExcelColumn(value = "失效日期", order = 9)
    private Date expiryDate;

    @ExcelColumn(value = "备注", order = 10)
    private String remark;
}
