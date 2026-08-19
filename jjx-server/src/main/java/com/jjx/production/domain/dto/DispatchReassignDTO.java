package com.jjx.production.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * REASSIGN 改派入参（P1-C）
 * 当前责任层更换责任人（同级换人，历史不可覆盖）。
 */
@Data
@Schema(description = "改派入参")
public class DispatchReassignDTO {

    @NotNull(message = "目标责任人必填")
    @Schema(description = "新责任人用户ID（同责任层）")
    private Long targetUserId;

    @Schema(description = "改派原因/备注（可空）")
    private String reason;
}
