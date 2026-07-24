package com.jjx.sales.domain.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
@Data @TableName("sales_receipt")
public class SalesReceipt {
    @TableId(type = IdType.AUTO)
    private Long receiptId;
    private String receiptNo;
    private Long invoiceId;
    private Long orderId;
    private Long customerId;
    private String customerName;
    private LocalDate receiptDate;
    private Integer receiptType;
    private Integer paymentMethod;
    private BigDecimal receiptAmount;
    private String currency;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
