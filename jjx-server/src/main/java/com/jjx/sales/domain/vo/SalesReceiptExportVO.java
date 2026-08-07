package com.jjx.sales.domain.vo;

import com.jjx.common.annotation.ExcelColumn;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 销售收款单导出视图对象（DEV-720）
 */
@Data
public class SalesReceiptExportVO {

    @ExcelColumn(value = "收款单号", order = 1)
    private String receiptNo;

    @ExcelColumn(value = "客户名称", order = 2)
    private String customerName;

    @ExcelColumn(value = "收款日期", order = 3)
    private LocalDate receiptDate;

    @ExcelColumn(value = "收款类型", order = 4)
    private String receiptTypeDesc;

    @ExcelColumn(value = "收款方式", order = 5)
    private String paymentMethodDesc;

    @ExcelColumn(value = "收款金额", order = 6)
    private BigDecimal receiptAmount;

    @ExcelColumn(value = "币种", order = 7)
    private String currency;

    @ExcelColumn(value = "状态", order = 8)
    private String statusDesc;

    @ExcelColumn(value = "备注", order = 9)
    private String remark;
}
