package com.jjx.inventory.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.math.BigDecimal;

@Data
public class ProductionSupplementPickDTO {
    @NotBlank
    private String reasonType;
    @NotBlank
    private String reason;
    private Long ncrId;
    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal productionQuantity;
    @NotEmpty
    private List<Map<String, Object>> items;
}
