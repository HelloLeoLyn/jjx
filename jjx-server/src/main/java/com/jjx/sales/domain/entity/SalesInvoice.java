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
    // DEV-933修复：表无 currency 列，标注不参与持久化（避免插入/查询报 Unknown column）
    @TableField(exist = false)
    private String currency;
    // DEV-933修复：表列名是 invoice_status，实体字段 status 需显式映射
    @TableField("invoice_status")
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
