package com.jjx.purchase.domain.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 收货导入批量校验请求项（DEV-726）
 * 对应导入模板的每一行
 */
@Data
public class ReceiptBatchCheckItemDTO {

    /** 行号（1-based，用于定位错误行） */
    private Integer rowIndex;

    /** 采购订单ID */
    private Long orderId;

    /** 订单明细ID */
    private Long itemId;

    /** 收货数量 */
    private BigDecimal receivedQuantity;

    /** 检验结果 */
    private String inspectionResult;

    /** 检验备注 */
    private String inspectionRemark;
}
