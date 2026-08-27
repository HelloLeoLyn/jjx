package com.jjx.production.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 生产任务流转事件（业务流水，非操作审计）
 * 对应表：production_task_event
 * <p>
 * 三职责严格分开：
 * - ProductionTask       = 当前责任状态（当前有效 task_quantity）
 * - ProductionTaskEvent  = 为什么变成现在这样（责任/数量变化历史）
 * - WorkReport           = 实际生产完成事实（不进入本表）
 * <p>
 * beforeTaskQuantity / afterTaskQuantity 唯一语义：
 * 记录 event.taskId 所代表 ProductionTask 的 task_quantity 动作前后值；
 * 任何 action 都不得改变该含义（不混入 remainingQuantity，不随 action 变化）。
 */
@Data
@TableName("production_task_event")
@Schema(description = "生产任务流转事件（业务流水）")
public class ProductionTaskEvent {

    @Schema(description = "事件ID")
    @TableId(type = IdType.AUTO)
    private Long eventId;

    @Schema(description = "动作主任务ID")
    private Long taskId;

    @Schema(description = "关联任务ID（ASSIGN=新child / RECALL=被收回child / RETURN=父任务）")
    private Long relatedTaskId;

    @Schema(description = "动作：ASSIGN/RECALL/RETURN/COMPLETE（历史保留 FIRST_ASSIGN/UNASSIGN 值）")
    private String action;

    @Schema(description = "操作人ID")
    private Long operatorId;

    @Schema(description = "操作人姓名")
    private String operatorName;

    @Schema(description = "动作前执行人ID")
    private Long fromAssigneeId;

    @Schema(description = "动作后执行人ID")
    private Long toAssigneeId;

    @Schema(description = "本次流转数量")
    private BigDecimal quantity;

    @Schema(description = "task_id 的 task_quantity 动作前值")
    private BigDecimal beforeTaskQuantity;

    @Schema(description = "task_id 的 task_quantity 动作后值")
    private BigDecimal afterTaskQuantity;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @Schema(description = "创建人")
    private String createBy;
}
