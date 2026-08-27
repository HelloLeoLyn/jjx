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
 * 生产任务（统一任务责任树）
 * 对应表：production_task
 * <p>
 * 领域规则（P1 Unified Production Task Foundation）：
 * - 第一层与所有下级完全同构，唯一差异是 parent_task_id 值
 * - parent_task_id = NULL 表示第一层真实业务 Task（不是 System Root）
 * - assignee_id 单值；NULL 仅允许出现在第一层（未分配 PENDING）
 * - task_quantity 是事实字段（创建/分配时快照，收回时条件扣减）
 * - completed/pending/assigned/remaining/has_children 等均为投影，不落库（P3/P4 对账）
 * - version 为乐观锁地基（P2 分配/收回/退回使用）
 */
@Data
@TableName("production_task")
@Schema(description = "生产任务（统一任务责任树）")
public class ProductionTask {

    @Schema(description = "任务ID（统一树节点ID）")
    @TableId(type = IdType.AUTO)
    private Long taskId;

    @Schema(description = "业务任务号：{工单号}-P{工序序号}-T{任务序号}")
    private String taskNo;

    @Schema(description = "工序执行ID（工序上下文）")
    private Long executionId;

    @Schema(description = "父任务ID；NULL=第一层真实任务")
    private Long parentTaskId;

    @Schema(description = "当前执行人ID（单值）；NULL=第一层未分配")
    private Long assigneeId;

    @Schema(description = "本任务获得的有效任务总量（快照事实）")
    private BigDecimal taskQuantity;

    @Schema(description = "状态：PENDING=未分配 / ACTIVE=已分配（P1 最小；完整状态机 P5）")
    private String status;

    @Schema(description = "乐观锁版本（P2 并发地基；配合 MyBatis-Plus OptimisticLocker 使用）")
    private Integer version;

    @Schema(description = "创建人")
    private String createBy;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @Schema(description = "更新人")
    private String updateBy;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
