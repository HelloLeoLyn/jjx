package com.jjx.production.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 任务流转事件行（P6 流水 Drawer）
 * 读取口径：task_id = 当前任务 OR related_task_id = 当前任务（双向关联可见）。
 */
@Data
@Schema(description = "任务流转事件行（业务流水）")
public class TaskEventVO {

    @Schema(description = "事件ID")
    private Long eventId;

    @Schema(description = "动作主任务ID")
    private Long taskId;

    @Schema(description = "关联任务ID（ASSIGN=新child / RECALL=被收回child / RETURN=父任务 / COMPLETE=null）")
    private Long relatedTaskId;

    @Schema(description = "动作：ASSIGN/RECALL/RETURN/COMPLETE（历史保留 FIRST_ASSIGN/UNASSIGN 值）")
    private String action;

    @Schema(description = "操作人ID")
    private Long operatorId;

    @Schema(description = "操作人姓名")
    private String operatorName;

    @Schema(description = "动作前执行人ID")
    private Long fromAssigneeId;

    @Schema(description = "动作前执行人姓名")
    private String fromAssigneeName;

    @Schema(description = "动作后执行人ID")
    private Long toAssigneeId;

    @Schema(description = "动作后执行人姓名")
    private String toAssigneeName;

    @Schema(description = "本次流转数量")
    private BigDecimal quantity;

    @Schema(description = "task_id 的 task_quantity 动作前值")
    private BigDecimal beforeTaskQuantity;

    @Schema(description = "task_id 的 task_quantity 动作后值")
    private BigDecimal afterTaskQuantity;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
