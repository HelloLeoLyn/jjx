package com.jjx.purchase.domain.dto;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
@Data
public class PurchaseMaterialDefaultDTO {
    @NotNull private Long supplierId;
    @NotNull private Long materialId;
    @NotBlank private String purchaseUnit;
    @NotBlank private String priceUnit;
}
