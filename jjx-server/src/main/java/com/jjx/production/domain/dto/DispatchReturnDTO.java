package com.jjx.production.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * RETURN 退回入参（P1-C）
 * 当前 ACTIVE 退回上级责任层。不传目标用户（系统自动返回历史 parent assignee）。
 */
@Data
@Schema(description = "退回入参")
public class DispatchReturnDTO {

    @NotBlank(message = "退回原因必填")
    @Schema(description = "退回原因")
    private String reason;
}
