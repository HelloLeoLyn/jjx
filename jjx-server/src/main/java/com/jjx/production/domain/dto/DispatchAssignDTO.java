package com.jjx.production.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 派工/改派入参（Legacy compatibility DTO. Do not use from Dispatch V1 frontend.）
 * P1-D 正式前端使用 DispatchAssignV1DTO / DispatchDelegateDTO / DispatchReassignDTO / DispatchReturnDTO。
 * level/transferFrom 仅用于理解旧客户端意图（P1-C legacy adapter）。
 */
@Data
@Schema(description = "派工入参（Legacy）")
public class DispatchAssignDTO {

    @Schema(description = "派工单ID（改派时必填，指派新工序时为空）")
    private Long dispatchId;

    @Schema(description = "工单ID（单工序指派/批量派工必填）")
    private Long orderId;

    @Schema(description = "工序执行记录ID（单工序指派必填）")
    private Long executionId;

    @Schema(description = "责任班组(部门ID)（可空，与设备/执行人至少一项）")
    private Long teamId;

    @Schema(description = "设备ID（可空=不限）")
    private Long equipmentId;

    @Schema(description = "执行人ID列表（可空，与班组/设备至少一项；多级链中为当前级别的人）")
    private List<Long> operatorIds;

    @Schema(description = "本次执行人级别（多级执行人链 1/2/3；空=第1级；转派时自动=转派人级别+1）")
    private Integer level;

    @Schema(description = "转派人用户ID（转派时必填：把任务转派给其手下，新人级别=转派人级别+1）")
    private Long transferFrom;

    @Schema(description = "执行人链是否已完整（true=可开工；false=还有下级执行人待追加，状态停在已派班组）")
    private Boolean chainComplete;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "是否工单批量（true=整单未派工工序统一指派）")
    private Boolean batch;
}
