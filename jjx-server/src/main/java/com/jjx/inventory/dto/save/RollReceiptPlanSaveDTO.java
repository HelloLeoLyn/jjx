package com.jjx.inventory.dto.save;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

/** 补录原采购明细的物理卷计划，不改变原收货/IQC事实。 */
@Data
public class RollReceiptPlanSaveDTO {
    @NotEmpty @Size(max = 500)
    private List<@Valid RollEntry> rolls;

    @Data
    public static class RollEntry {
        @NotBlank @Size(max = 64) private String rollNo;
        @NotNull @DecimalMin(value = "0", inclusive = false) private BigDecimal widthMm;
        @NotNull @DecimalMin(value = "0", inclusive = false) private BigDecimal lengthM;
        /** 只读展示，后端计算；JSON计划不保存面积副本。 */
        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        private BigDecimal area;
    }
}
