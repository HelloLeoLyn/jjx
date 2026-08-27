package com.jjx.production.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 任务分配明细项（P2 Task Flow）
 * <p>
 * 首次分配（assignee_id IS NULL）时：
 * - 只使用 assigneeId，quantity 不是业务输入
 * - 若携带 quantity，后端必须校验其等于 task.task_quantity（First Task 数量不可修改）
 * 后续分配（多人）：quantity = 分配给该执行人的当前有效责任量。
 */
@Data
@Schema(description = "任务分配明细项")
public class TaskAssignItemDTO {

    @NotNull(message = "执行人必填")
    @Schema(description = "目标执行人ID")
    private Long assigneeId;

    @DecimalMin(value = "0.01", message = "分配数量必须大于0")
    @Digits(integer = 14, fraction = 2, message = "分配数量精度超限")
    @Schema(description = "分配数量（首次分配时非业务输入，须等于任务数量）")
    private BigDecimal quantity;
}
