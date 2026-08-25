package com.jjx.production.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 退回入参（P2 Task Flow）
 * 当前执行人把自己尚未完成、尚未再下发的剩余退给父任务；
 * 第一层任务（parent_task_id IS NULL）禁止退回。
 */
@Data
@Schema(description = "退回入参")
public class TaskReturnDTO {

    @NotNull(message = "退回数量必填")
    @DecimalMin(value = "0.01", message = "退回数量必须大于0")
    @Digits(integer = 14, fraction = 2, message = "退回数量精度超限")
    @Schema(description = "退回数量（<= 当前任务自身剩余）")
    private BigDecimal quantity;

    @Schema(description = "备注")
    private String remark;
}
