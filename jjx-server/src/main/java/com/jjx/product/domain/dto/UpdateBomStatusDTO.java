package com.jjx.product.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateBomStatusDTO {
    @NotNull(message = "bomId 不能为空")
    private Long bomId;
    private Integer current;
    private Integer target;
    private String remark;
}
