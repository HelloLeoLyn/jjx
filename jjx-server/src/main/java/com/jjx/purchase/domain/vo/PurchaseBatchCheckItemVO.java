package com.jjx.purchase.domain.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 采购导入批量校验结果项（DEV-726：模式③批量校验导入）
 * 对应导入表格的每一行
 */
@Data
public class PurchaseBatchCheckItemVO {

    /** 行号（1-based，用于前端定位错误行） */
    private Integer rowIndex;

    /** 校验状态：ok / error */
    private String status;

    /** 回填ID（付款ID/发票ID等，查到记录时回填） */
    private Long bizId;

    /** 单据编号（回填） */
    private String bizNo;

    /** 字段级错误列表（status=error 时填充） */
    private List<FieldError> errors = new ArrayList<>();

    /** 错误类型快捷标记：DUPLICATE / NOT_FOUND / MISSING_REQUIRED / INVALID */
    private String errorType;

    @Data
    public static class FieldError {
        /** 字段名：paymentNo / orderId / documentNo / itemId / receivedQuantity ... */
        private String field;
        /** 错误类型 */
        private String type;
        /** 错误描述 */
        private String message;
    }
}
