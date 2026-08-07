package com.jjx.purchase.domain.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 发票导入批量校验请求项（DEV-726）
 * 对应导入模板的每一行
 */
@Data
public class DocumentBatchCheckItemDTO {

    /** 行号（1-based，用于定位错误行） */
    private Integer rowIndex;

    /** 发票编号 */
    private String documentNo;

    /** 采购订单ID */
    private Long orderId;

    /** 供应商ID */
    private Long supplierId;

    /** 开票日期 */
    private LocalDate documentDate;

    /** 发票金额 */
    private BigDecimal documentAmount;

    /** 币种 */
    private String currency;

    /** 备注 */
    private String remark;
}
