package com.jjx.production.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 派工/改派入参
 */
@Data
@Schema(description = "派工入参")
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

    @Schema(description = "执行人ID列表（可空，与班组/设备至少一项）")
    private List<Long> operatorIds;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "是否工单批量（true=整单未派工工序统一指派）")
    private Boolean batch;
}
