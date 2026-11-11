package com.jjx.inventory.dto.save;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class RollReturnSaveDTO {
    @NotNull private Long sourceOutboundItemId;
    @NotBlank @Size(max = 64) private String rollNo;
    @NotNull @DecimalMin(value = "0", inclusive = false) private BigDecimal returnedLengthM;
    @NotNull private Long warehouseId;
    private Long locationId;
    @NotBlank @Size(max = 200) private String reason;
}
