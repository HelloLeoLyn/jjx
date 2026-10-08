package com.jjx.sales.domain.vo;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.util.Date;
@Data
public class DeliveryArrangeLineVO {
    private Long id;
    private Long orderId;
    private String orderNo;
    private Long customerId;
    private String customerName;
    private String deliveryAddress;
    private String contactPerson;
    private String contactPhone;
    private String currency;
    private String deliveryMethod;
    @JsonFormat(pattern="yyyy-MM-dd") private Date dueDate;
    private Long productId;
    private String productCode;
    private String productName;
    private String customerMaterialNo;
    private String specification;
    private String unit;
    private Integer quantity;
    private Integer shipped;
    private Integer occupied;
    private Integer orderRemainingQuantity;
    private Integer availableQuantity;
    private Integer shortageQuantity;
    private BigDecimal ownStockAvailable;
    private BigDecimal sharedStockAvailable;
    private BigDecimal productStockAvailable;
    private BigDecimal unitPrice;
    private BigDecimal stockAvailable;
    private String blockedReason;
}
