package com.jjx.production.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 生产报工提交入参（P2-C）
 * 客户端不提交：orderId/orderNo/reporterId/reporterName/equipmentName/reportStatus/reportTime
 * ——全部由后端根据上下文生成或解析。
 */
@Data
@Schema(description = "报工提交入参")
public class WorkReportSubmitDTO {

    @NotNull(message = "工序执行ID必填")
    @Schema(description = "工序执行记录ID")
    private Long executionId;

    @NotNull(message = "任务节点ID必填")
    @Schema(description = "任务树节点ID（P2 起报工必须绑定 TaskNode，且当前用户须为该节点持有人）")
    private Long taskNodeId;

    @Schema(description = "本次合格数量（>=0，与不良之和>0）")
    private BigDecimal qualifiedQuantity;

    @Schema(description = "本次不良数量（>=0，与合格之和>0；>0 时不良原因必填）")
    private BigDecimal defectiveQuantity;

    @Schema(description = "本次人工工时（>=0，可空=0）")
    private BigDecimal laborHours;

    @Schema(description = "本次机器工时（>=0，可空=0）")
    private BigDecimal machineHours;

    @Schema(description = "本次生产开始时间（可空；与 end 同时传）")
    private LocalDateTime workStartTime;

    @Schema(description = "本次生产结束时间（可空；与 start 同时传）")
    private LocalDateTime workEndTime;

    @Schema(description = "本次实际使用设备ID（可空=默认用 execution 设备）")
    private Long equipmentId;

    @Schema(description = "不良原因（defectiveQuantity>0 时必填）")
    private String defectReason;

    @Schema(description = "备注（可空，提交后不可变）")
    private String remark;
}
