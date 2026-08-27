package com.jjx.production.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 任务分配入参（P2 Task Flow）
 * <p>
 * 统一入口 POST /production/tasks/{taskId}/assign：
 * - 每个层级行为同构：items 可 1 条或多条，逐条分配数量，合计不超过任务剩余（允许部分分配）
 * - 身份门：assignee_id IS NULL → 仅生产管理者可发起；已分配 → 仅当前执行人可发起
 */
@Data
@Schema(description = "任务分配入参")
public class TaskAssignDTO {

    @Valid
    @NotEmpty(message = "分配明细不能为空")
    @Schema(description = "分配明细（每项分配数量须大于 0，合计不超过任务剩余）")
    private List<TaskAssignItemDTO> items;

    @Schema(description = "备注")
    private String remark;
}
