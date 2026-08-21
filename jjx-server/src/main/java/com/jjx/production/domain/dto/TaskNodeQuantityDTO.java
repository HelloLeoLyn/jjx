package com.jjx.production.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 任务节点数量操作入参（P2：收回/退回）
 * quantity 必须大于 0 且不得超过节点当前可操作数量（服务端校验）。
 */
@Data
@Schema(description = "任务节点数量操作入参（收回/退回）")
public class TaskNodeQuantityDTO {

    @NotNull(message = "数量必填")
    @Schema(description = "收回/退回数量（必须大于 0）")
    private BigDecimal quantity;
}
