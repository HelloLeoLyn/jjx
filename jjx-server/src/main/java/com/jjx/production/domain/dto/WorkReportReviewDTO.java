package com.jjx.production.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 报工审批入参（P3）
 * approve：reviewRemark 可空；reject：reviewRemark（驳回原因）必填。
 */
@Data
@Schema(description = "报工审批入参")
public class WorkReportReviewDTO {

    @Schema(description = "审批备注（驳回必填；通过可空）")
    private String reviewRemark;
}
