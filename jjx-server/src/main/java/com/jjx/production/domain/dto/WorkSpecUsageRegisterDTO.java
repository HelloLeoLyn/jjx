package com.jjx.production.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 工单换版执行区间登记入参（dev-20261011-013）
 */
@Data
@Schema(description = "工单换版执行区间登记")
public class WorkSpecUsageRegisterDTO {

    @Schema(description = "生产工单ID")
    @NotNull(message = "生产工单ID不能为空")
    private Long workOrderId;

    @Schema(description = "新的作业规范发布版本ID（须属同产品且已发布）")
    @NotNull(message = "作业规范版本不能为空")
    private Long specVersionId;

    @Schema(description = "生效数量区间-起（含），可空")
    private BigDecimal qtyFrom;

    @Schema(description = "生效数量区间-止（含），可空")
    private BigDecimal qtyTo;

    @Schema(description = "生效开始时间，可空")
    private LocalDateTime startTime;

    @Schema(description = "生效结束时间，可空")
    private LocalDateTime endTime;

    @Schema(description = "换版原因")
    private String changeReason;

    @Schema(description = "批准人；留空则取当前登录人显示名")
    private String approvedBy;
}
