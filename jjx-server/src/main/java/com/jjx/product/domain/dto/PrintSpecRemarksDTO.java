package com.jjx.product.domain.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.Map;

/** 仅接受整组备注，不能经此入口修改工艺路线。 */
@Data
public class PrintSpecRemarksDTO {
    @NotNull @Size(max = 64) private String revision;
    @NotNull @Size(max = 3)
    private Map<String, @NotNull @Size(max = 1000) String> remarks;
}
