package com.jjx.production.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 报工撤销入参（P2-C）
 * 只允许 cancelReason；不允许修改其他字段。
 */
@Data
@Schema(description = "报工撤销入参")
public class WorkReportCancelDTO {

    @NotBlank(message = "撤销原因必填")
    @Schema(description = "撤销原因")
    private String cancelReason;
}
