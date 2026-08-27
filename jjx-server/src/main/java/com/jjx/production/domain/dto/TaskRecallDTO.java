package com.jjx.production.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 收回入参（P2 Task Flow）
 * 父执行人从自己的直接 Child 拿回尚未完成、尚未再下发的量。
 * 必须确认 child.parent_task_id == parentTaskId，禁止跨树/越级。
 */
@Data
@Schema(description = "收回入参")
public class TaskRecallDTO {

    @NotNull(message = "子任务必填")
    @Schema(description = "直接子任务ID（被收回方）")
    private Long childTaskId;

    @NotNull(message = "收回数量必填")
    @DecimalMin(value = "0.01", message = "收回数量必须大于0")
    @Digits(integer = 14, fraction = 2, message = "收回数量精度超限")
    @Schema(description = "收回数量（<= child 当前剩余）")
    private BigDecimal quantity;

    @Schema(description = "备注")
    private String remark;
}
