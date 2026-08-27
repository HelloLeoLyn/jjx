package com.jjx.production.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 任务完成入参（P5 人工确认完成；完成前置条件由 service 统一校验）
 */
@Data
@Schema(description = "任务完成入参")
public class TaskCompleteDTO {

    @Schema(description = "完成备注")
    private String remark;
}
