package com.jjx.production.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 任务分配明细（一次分配可多人）
 * 分配任务 = 在父节点下创建子 TaskNode；数量必须大于 0；合计可小于父节点可分配数量。
 */
@Data
@Schema(description = "任务分配明细")
public class TaskAssignItemDTO {

    @NotNull(message = "分配人ID必填")
    @Schema(description = "被分配人（子节点持有人）用户ID")
    private Long userId;

    @NotNull(message = "分配数量必填")
    @Schema(description = "分配数量（必须大于 0）")
    private BigDecimal quantity;
}
