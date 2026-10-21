package com.jjx.product.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TempStandardProcessDTO {
    @NotBlank @Size(max = 100)
    private String processName;
    @NotBlank @Size(max = 20)
    private String processCategory;
    @NotBlank @Size(max = 500)
    private String description;
    private boolean force;
}
