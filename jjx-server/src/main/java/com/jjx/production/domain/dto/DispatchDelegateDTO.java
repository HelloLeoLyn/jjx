package com.jjx.production.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * DELEGATE 下派入参（P1-C）
 * 当前 ACTIVE 责任人把任务向下交给新责任人。
 */
@Data
@Schema(description = "下派入参")
public class DispatchDelegateDTO {

    @NotNull(message = "目标责任人必填")
    @Schema(description = "新责任人用户ID（须在当前责任人可派范围内）")
    private Long targetUserId;

    @Schema(description = "备注（可空）")
    private String remark;
}
