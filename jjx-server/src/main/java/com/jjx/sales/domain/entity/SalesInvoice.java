package com.jjx.sales.domain.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
@Data @TableName("sales_invoice")
public class SalesInvoice {
    @TableId(type = IdType.AUTO)
    private Long invoiceId;
    private String invoiceNo;
    private Long orderId;
    private Long customerId;
    private String customerName;
    private Integer invoiceType;
    private LocalDate invoiceDate;
    private String taxpayerId;
    private String address;
    private String phone;
    private String bankName;
    private String bankAccount;
    private BigDecimal invoiceAmount;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private String currency;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
