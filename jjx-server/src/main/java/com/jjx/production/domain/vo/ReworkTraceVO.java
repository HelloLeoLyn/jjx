package com.jjx.production.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 返工链投影（只读）—— dev-20260923-031 一期。
 *
 * <p>把「不良单 → 返工工序 → 报工 → 复检 → 回收」这一条链投影成一个对象，
 * 供工序执行页（贴返工身份）与不良台账页（显示返工进度）共用，避免前端拼多次请求、也避免页面出现裸 ID。</p>
 */
@Data
@Schema(description = "返工链投影：一条返工从不良单到复检回收的进度")
public class ReworkTraceVO {

    @Schema(description = "不良单ID")
    private Long ncrId;

    @Schema(description = "不良单号（页面直接展示，不用再给 ID）")
    private String ncrNo;

    @Schema(description = "不良数量")
    private BigDecimal defectQuantity;

    @Schema(description = "已处置数量")
    private BigDecimal disposedQuantity;

    @Schema(description = "处置动作ID")
    private Long actionId;

    @Schema(description = "处置动作状态：PROCESSING 返工在制 / DONE 返工闭环完成")
    private String actionStatus;

    @Schema(description = "本次返工件数")
    private BigDecimal reworkQuantity;

    @Schema(description = "返工工序ID（前端用它把标签贴到工序列表行上）")
    private Long executionId;

    @Schema(description = "返工工序名称")
    private String processName;

    @Schema(description = "返工任务ID")
    private Long taskId;

    @Schema(description = "返工任务编号")
    private String taskNo;

    @Schema(description = "一级责任人名下的返工任务状态")
    private String taskStatus;

    @Schema(description = "返工任务一级责任人")
    private String taskAssigneeName;

    @Schema(description = "返工任务是否已向实际执行人分派子任务")
    private Boolean hasWorkerTasks;

    @Schema(description = "返工工序状态（ExecutionStatusEnum 值：0待执行/1准备中/2执行中/3已暂停/4已完成/5已跳过/6已取消）")
    private Integer executionStatus;

    @Schema(description = "返工要求 / 作业说明（来自工序 custom_process_params）")
    private String reworkRequirement;

    @Schema(description = "返工已报工数量（APPROVED 合计）")
    private BigDecimal reportedQuantity;

    @Schema(description = "复检批ID")
    private Long reinspectionLotId;

    @Schema(description = "复检批号")
    private String reinspectionLotNo;

    @Schema(description = "复检批状态")
    private String reinspectionStatus;

    @Schema(description = "复检结果")
    private String reinspectionResult;

    @Schema(description = "已回收良品数（复检批合格量）")
    private BigDecimal recoveredQuantity;

    @Schema(description = "一句人话：现在到哪一步、下一步谁做什么")
    private String statusText;
}
