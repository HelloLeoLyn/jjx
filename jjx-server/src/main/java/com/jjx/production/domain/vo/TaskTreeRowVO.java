package com.jjx.production.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 统一任务树行 VO（第一层与所有下级完全同构；P1 Unified Production Task Foundation + P2 Task Flow）
 * <p>
 * 数量投影规则（P4 Completion Projection & Reconciliation）：
 * - completedQuantity  = 当前 Task 整个有效 subtree 的 APPROVED WorkReport 合计（展示归集）
 * - pendingQuantity    = 当前 Task 整个有效 subtree 的 PENDING WorkReport 合计（展示归集）
 * - assignedQuantity   = 展示值：下游仍未 completed/pending 的有效责任量
 *                       = childAssigned - (subtreeCompleted - ownCompleted) - (subtreePending - ownPending)
 *                       （仅 UI 展示；写 gate 一律使用 childAssigned + ownRemaining，禁止使用该展示值）
 * - remainingQuantity  = gate 口径：taskQuantity - childAssigned - ownPending - ownCompleted（下限 0）
 * 树级展示不变式：taskQuantity = completedQuantity + pendingQuantity + assignedQuantity + remainingQuantity
 * <p>
 * children 永远非 null：
 * - hasChildren=false + children=[] ：确定无直接子任务（叶子；活动树排除 CANCELLED）
 * - hasChildren=true  + children=[] ：存在直接子任务，尚未懒加载
 * - hasChildren=true  + children=[...]：直接子任务已加载
 */
@Data
@Schema(description = "统一任务树行VO")
public class TaskTreeRowVO {

    @Schema(description = "任务ID（统一树节点ID）")
    private Long taskId;

    @Schema(description = "业务任务号")
    private String taskNo;

    @Schema(description = "父任务ID；null=第一层")
    private Long parentTaskId;

    @Schema(description = "工序执行ID（工序上下文）")
    private Long executionId;

    @Schema(description = "工单编号（展示上下文）")
    private String orderNo;

    @Schema(description = "工序名称（展示上下文）")
    private String processName;

    @Schema(description = "工序编码（展示上下文）")
    private String processCode;

    @Schema(description = "工序顺序（展示上下文）")
    private Integer processOrder;

    @Schema(description = "当前执行人ID；null=未分配")
    private Long assigneeId;

    @Schema(description = "当前执行人姓名（join sys_user 投影）；null=未分配")
    private String assigneeName;

    @Schema(description = "上级执行人姓名（任务来源展示）")
    private String parentAssigneeName;

    @Schema(description = "任务数量（本 Task 获得的有效任务总量）")
    private BigDecimal taskQuantity;

    @Schema(description = "已完成（当前 Task 整棵有效子树 APPROVED WorkReport 合计；点击查看明细）")
    private BigDecimal completedQuantity;

    @Schema(description = "待审批（当前 Task 整棵有效子树 PENDING WorkReport 合计）")
    private BigDecimal pendingQuantity;

    @Schema(description = "已下发 = SUM(直接子节点 task_quantity)（投影）")
    private BigDecimal assignedQuantity;

    @Schema(description = "当前剩余（gate 口径）= taskQuantity - childAssigned - ownPending - ownCompleted（下限 0）")
    private BigDecimal remainingQuantity;

    @Schema(description = "状态（P5 生命周期，与 assignee_id 解耦）：PENDING未进入责任执行 / ACTIVE进行中 / COMPLETED人工确认完成 / CANCELLED责任取消或归零")
    private String status;

    @Schema(description = "状态描述")
    private String statusLabel;

    @Schema(description = "是否具备分配能力（有可分配下属；未分配=当前登录人，已分配=assignee）")
    private Boolean canAssign;

    @Schema(description = "是否有直接子任务（投影）")
    private Boolean hasChildren;

    @Schema(description = "直接子任务（懒加载挂载；永远非 null，至少 []）")
    private List<TaskTreeRowVO> children = new ArrayList<>();

    @Schema(description = "允许的动作（P5 后端统一投影：ASSIGN/RECALL/RETURN/COMPLETE/FLOW；前端只按此渲染）")
    private List<String> allowedActions = new ArrayList<>();
}
