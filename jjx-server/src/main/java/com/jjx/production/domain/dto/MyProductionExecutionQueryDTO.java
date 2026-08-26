package com.jjx.production.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "我的生产任务分页查询")
public class MyProductionExecutionQueryDTO {
    private Integer pageNum = 1;
    private Integer pageSize = 10;
    private String orderNo;
    private String processName;
    private String executionStatus;
    private Long equipmentId;
}
