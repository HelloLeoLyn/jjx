package com.jjx.production.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 生产报工（一次不可覆盖的生产数量/工时事实 + 审批事实）
 * 对应表：production_work_report
 * 关系：ProductionTask 1:N ProductionWorkReport（task_id 锚点）
 * <p>
 * 领域规则（P3 WorkReport + Approval）：
 * - 报工只能由 Task 当前执行人提交；数量上限 = Task.remainingQuantity（唯一额度边界）
 * - 提交 = INSERT status=PENDING（占用额度，不计 completed）；不修改 Task.task_quantity
 * - 审批一次：PENDING → APPROVED（计入 completed）/ REJECTED（释放额度）；CANCELLED 仅 PENDING 可撤
 * - APPROVED 为有效完成事实，禁止普通撤销；更正 = 专门冲销/作废（P4）
 * - 生产事实字段（数量/工时/时间/报工人）提交后不可覆盖；禁止物理删除
 * - 已关联 PASS/FAIL 质检的报工禁止 reject/cancel；仅 PENDING 质检可联动逻辑删除
 */
@Data
@TableName("production_work_report")
public class ProductionWorkReport {

    @TableId(type = IdType.AUTO)
    private Long reportId;

    /** 报工单号：WR-YYYYMMDD-NNNN */
    private String reportNo;

    /** 生产订单ID（冗余引用，便于追溯查询） */
    private Long orderId;

    /** 工单编号（冗余） */
    private String orderNo;

    /** 工序执行ID（工序上下文；生命周期 gate 使用） */
    private Long executionId;

    /** 生产任务ID（报工锚点：Task 当前执行人提交；数量 <= Task.remainingQuantity） */
    private Long taskId;

    /** 报工提交人ID（当前 Task 执行人） */
    private Long reporterId;

    /** 报工提交人姓名快照（历史事实：人员改名后保持当时姓名） */
    private String reporterName;

    /** 代操作人ID（空=本人报工） */
    private Long proxyId;

    /** 代操作人姓名快照 */
    private String proxyName;

    /** 本次实际使用设备ID（可空=人工工序无设备） */
    private Long equipmentId;

    /** 本次实际使用设备名称（快照） */
    private String equipmentName;

    /** 本次合格数量 */
    private BigDecimal qualifiedQuantity;

    /** 本次不良数量 */
    private BigDecimal defectiveQuantity;

    /** 本次人工工时 */
    private BigDecimal laborHours;

    /** 本次机器工时 */
    private BigDecimal machineHours;

    /** 本次生产开始时间（可空） */
    private LocalDateTime workStartTime;

    /** 本次生产结束时间（可空；校验 end>=start） */
    private LocalDateTime workEndTime;

    /** 报工正式提交时间 */
    private LocalDateTime reportTime;

    /** 不良原因（defectiveQuantity>0 时必填） */
    private String defectReason;

    /** 备注（提交后不可变） */
    private String remark;

    /** 状态：PENDING/APPROVED/REJECTED/CANCELLED */
    private String reportStatus;

    /** 提交时点应审批人ID（空=生产管理兜底） */
    private Long pendingReviewerId;

    /** 提交时点应审批人姓名快照 */
    private String pendingReviewerName;

    /** 审批人ID（approve/reject 落库；一次审批） */
    private Long reviewerId;

    /** 审批人姓名快照 */
    private String reviewerName;

    /** 审批时间 */
    private LocalDateTime reviewTime;

    /** 审批备注（驳回必填） */
    private String reviewRemark;

    /** 撤销人ID */
    private Long cancelledBy;

    /** 撤销人姓名 */
    private String cancelledByName;

    /** 撤销时间 */
    private LocalDateTime cancelledAt;

    /** 撤销原因（必填） */
    private String cancelReason;

    private String createBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    private String updateBy;
    @TableField(fill = FieldFill.UPDATE)
    private LocalDateTime updateTime;
}
