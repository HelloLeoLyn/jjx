package com.jjx.production.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 任务完成明细行（P4：当前 Task 有效子树内 APPROVED WorkReport）
 * 对账 invariant：SUM(reportQuantity) == TaskTreeRowVO.completedQuantity
 */
@Data
@Schema(description = "任务完成明细行（subtree 内 APPROVED WorkReport）")
public class TaskCompletionDetailVO {

    @Schema(description = "报工ID")
    private Long reportId;

    @Schema(description = "报工所属任务ID")
    private Long taskId;

    @Schema(description = "任务当前执行人ID")
    private Long taskAssigneeId;

    @Schema(description = "任务当前执行人姓名（join sys_user 投影）")
    private String taskAssigneeName;

    @Schema(description = "报工提交人ID")
    private Long reporterId;

    @Schema(description = "报工提交人姓名快照")
    private String reporterName;

    @Schema(description = "工序执行ID")
    private Long executionId;

    @Schema(description = "工单编号")
    private String orderNo;

    @Schema(description = "工序名称")
    private String processName;

    @Schema(description = "合格数量")
    private BigDecimal qualifiedQuantity;

    @Schema(description = "不良数量")
    private BigDecimal defectiveQuantity;

    @Schema(description = "报工数量 = qualified + defective")
    private BigDecimal reportQuantity;

    @Schema(description = "报工时间")
    private LocalDateTime reportTime;

    @Schema(description = "审批人姓名快照")
    private String reviewerName;

    @Schema(description = "审批时间")
    private LocalDateTime reviewTime;

    @Schema(description = "备注")
    private String remark;
}
