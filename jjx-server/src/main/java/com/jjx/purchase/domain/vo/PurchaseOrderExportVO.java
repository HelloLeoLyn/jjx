package com.jjx.purchase.domain.vo;

import com.jjx.common.annotation.ExcelColumn;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 采购订单导出视图对象
 */
@Data
public class PurchaseOrderExportVO {

    @ExcelColumn(value = "订单号", order = 1)
    private String orderNo;

    @ExcelColumn(value = "供应商名称", order = 2)
    private String supplierName;

    @ExcelColumn(value = "订单日期", order = 4)
    private LocalDate orderDate;

    @ExcelColumn(value = "期望交货日期", order = 5)
    private LocalDate expectedDeliveryDate;

    @ExcelColumn(value = "订单金额", order = 6)
    private BigDecimal orderAmount;

    @ExcelColumn(value = "税额", order = 7)
    private BigDecimal orderTax;

    @ExcelColumn(value = "含税总金额", order = 8)
    private BigDecimal orderTotalAmount;

    @ExcelColumn(value = "币种", order = 9)
    private String currency;

    @ExcelColumn(value = "已付款金额", order = 13)
    private BigDecimal paidAmount;

    @ExcelColumn(value = "合同编号", order = 14)
    private String contractNo;

    @ExcelColumn(value = "交货方式", order = 15)
    private String deliveryMethod;

    @ExcelColumn(value = "交货地址", order = 16)
    private String deliveryAddress;

    @ExcelColumn(value = "备注", order = 18)
    private String remark;

    @ExcelColumn(value = "创建人", order = 19)
    private String createBy;

    @ExcelColumn(value = "创建时间", order = 20)
    private LocalDateTime createTime;
}
