package com.jjx.sales.domain.vo;

import com.jjx.common.annotation.ExcelColumn;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 销售发票导出视图对象（DEV-720）
 */
@Data
public class SalesInvoiceExportVO {

    @ExcelColumn(value = "发票编号", order = 1)
    private String invoiceNo;

    @ExcelColumn(value = "客户名称", order = 2)
    private String customerName;

    @ExcelColumn(value = "开票日期", order = 3)
    private LocalDate invoiceDate;

    @ExcelColumn(value = "发票类型", order = 4)
    private String invoiceTypeDesc;

    @ExcelColumn(value = "发票金额", order = 5)
    private BigDecimal invoiceAmount;

    @ExcelColumn(value = "税额", order = 6)
    private BigDecimal taxAmount;

    @ExcelColumn(value = "价税合计", order = 7)
    private BigDecimal totalAmount;

    @ExcelColumn(value = "币种", order = 8)
    private String currency;

    @ExcelColumn(value = "状态", order = 9)
    private String statusDesc;

    @ExcelColumn(value = "备注", order = 10)
    private String remark;
}
