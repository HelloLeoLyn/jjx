package com.jjx.sales.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;

/** 样品订单一对一产品扩展。 */
@Data
@TableName("sales_sample_order")
public class SalesSampleOrder {
    @TableId(type = IdType.AUTO)
    private Long sampleOrderId;
    private Long orderId;
    private Long productId;
    private String productCode;
    private String productName;
    private String productSpecification;
    private String customerMaterialNo;
    private Integer sampleQty;
    private String unit;
    private BigDecimal unitPrice;
    private BigDecimal amount;
    private String productRemark;
    private Integer sampleStatus;
    private Integer sampleRound;
    private String engineeringNote;
    private String engineeringAcceptor;
    private Date engineeringAcceptTime;
    private String rejectReason;
    private String currentProcess;
    private BigDecimal sampleCost;
    private BigDecimal sampleWorkHours;
    private String sampleTrackingNo;
    private Date sampleSendDate;
    private Date sampleConfirmDate;
    private String confirmBy;
    private String confirmMethod;
    private LocalDateTime confirmTime;
    private LocalDateTime confirmSentTime;
    private String sampleClientName;
    private Long convertedOrderId;
    private Date convertOrderTime;
    private String formalVersion;
    private LocalDateTime lastTransferTime;
    private String createBy;
    private Date createTime;
    private String updateBy;
    private Date updateTime;
    private Integer deleted;
}
