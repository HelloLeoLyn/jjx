package com.jjx.purchase.domain.vo;

import com.jjx.common.annotation.ExcelColumn;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 采购订单明细导出视图对象
 */
@Data
public class PurchaseOrderItemExportVO {

    @ExcelColumn(value = "订单号", order = 1)
    private String orderNo;

    @ExcelColumn(value = "物料编码", order = 2)
    private String materialCode;

    @ExcelColumn(value = "物料名称", order = 3)
    private String materialName;

    @ExcelColumn(value = "物料规格", order = 4)
    private String materialSpec;

    @ExcelColumn(value = "单位", order = 5)
    private String unit;

    @ExcelColumn(value = "订单数量", order = 6)
    private BigDecimal quantity;

    @ExcelColumn(value = "单价", order = 7)
    private BigDecimal unitPrice;

    @ExcelColumn(value = "金额", order = 8)
    private BigDecimal amount;

    @ExcelColumn(value = "已收货数量", order = 9)
    private BigDecimal receivedQuantity;

    @ExcelColumn(value = "收货状态", order = 10)
    private String receiptStatusName;

    @ExcelColumn(value = "批次号", order = 11)
    private String batchNo;

    @ExcelColumn(value = "生产日期", order = 12)
    private LocalDate productionDate;

    @ExcelColumn(value = "有效期至", order = 13)
    private LocalDate expiryDate;

    @ExcelColumn(value = "检验结果", order = 14)
    private String inspectionResult;

    @ExcelColumn(value = "检验备注", order = 15)
    private String inspectionRemark;
}
