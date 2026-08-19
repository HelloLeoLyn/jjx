package com.jjx.production.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * ASSIGN 初始派工入参（P1-D 正式 V1 API）
 * 不包含 level / transferFrom / chainComplete（固定 1/2/3 级语义已从正式 API 移除）。
 */
@Data
@Schema(description = "初始派工入参（V1 正式 API）")
public class DispatchAssignV1DTO {

    @NotNull(message = "工序执行ID必填")
    @Schema(description = "工序执行记录ID")
    private Long executionId;

    @NotNull(message = "工单ID必填")
    @Schema(description = "工单ID")
    private Long orderId;

    @NotNull(message = "责任人必填")
    @Schema(description = "第 1 级责任人用户ID（初始派工目标）")
    private Long targetUserId;

    @Schema(description = "设备ID（可空=不限）")
    private Long equipmentId;

    @Schema(description = "备注（可空）")
    private String remark;
}
