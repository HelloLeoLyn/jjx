package com.jjx.production.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 任务树第一层分页查询入参（P1 最小 + P6 关键词/状态过滤）
 */
@Data
@Schema(description = "任务树第一层分页查询入参")
public class TaskTreeQueryDTO {

    @Schema(description = "页码", example = "1")
    private Integer pageNum = 1;

    @Schema(description = "每页大小", example = "10")
    private Integer pageSize = 10;

    @Schema(description = "关键词（工单号/工序名模糊匹配）")
    private String keyword;

    @Schema(description = "状态过滤：PENDING/ACTIVE/COMPLETED/CANCELLED（可空=全部）")
    private String status;
}
