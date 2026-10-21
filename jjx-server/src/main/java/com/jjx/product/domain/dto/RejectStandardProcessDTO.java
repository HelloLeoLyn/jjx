package com.jjx.product.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RejectStandardProcessDTO {
    @NotBlank @Size(max = 500)
    private String rejectReason;
}
