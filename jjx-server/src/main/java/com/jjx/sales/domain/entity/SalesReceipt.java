package com.jjx.sales.domain.entity;
import com.baomidou.mybatisplus.annotation.*;
import com.jjx.common.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
import java.time.LocalDate;
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sales_receipt")
public class SalesReceipt extends BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long receiptId;
    private String receiptNo;
    private Long orderId;
    private Long customerId;
    private String customerName;
    private LocalDate receiptDate;
    private Integer receiptType;
    private Integer paymentMethod;
    private BigDecimal receiptAmount;
    // DEV-934修复：实体补 actualAmount（表列 actual_amount NOT NULL 无默认值，前端不传时服务端默认=receiptAmount）
    private BigDecimal actualAmount;
    private String currency;
    @TableField("receipt_status")
    private Integer status;
}
