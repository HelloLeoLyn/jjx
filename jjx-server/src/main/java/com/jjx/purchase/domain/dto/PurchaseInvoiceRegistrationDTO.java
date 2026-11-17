package com.jjx.purchase.domain.dto;

import com.jjx.sales.domain.dto.AttachmentBindDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 一张真实发票登记一次，可附多份文件。 */
@Data
public class PurchaseInvoiceRegistrationDTO {
    @NotNull @Positive
    private Long orderId;
    @NotBlank @Size(max = 50)
    private String invoiceNo;
    @NotNull
    private LocalDate documentDate;
    @NotBlank @Size(max = 200)
    private String sellerName;
    @NotBlank @Size(max = 50)
    private String sellerTaxNo;
    @NotBlank @Size(max = 200)
    private String buyerName;
    @NotBlank @Size(max = 50)
    private String buyerTaxNo;
    @NotNull @Digits(integer = 10, fraction = 2)
    private BigDecimal untaxedAmount;
    @NotNull @Digits(integer = 10, fraction = 2)
    private BigDecimal taxAmount;
    @NotNull @Digits(integer = 10, fraction = 2)
    private BigDecimal documentAmount;
    @NotBlank @Size(max = 10)
    private String currency;
    @Size(max = 500)
    private String remark;
    @Valid @NotNull @Size(max = 50)
    private List<@NotNull AttachmentBindDTO> attachments;
}
