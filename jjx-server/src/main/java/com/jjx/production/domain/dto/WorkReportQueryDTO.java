package com.jjx.production.domain.dto;

import com.jjx.common.core.page.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 报工查询入参（P3：mine / pending-approval 分页过滤）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "报工查询入参")
public class WorkReportQueryDTO extends PageQuery {

    @Schema(description = "报工状态：PENDING/APPROVED/REJECTED/CANCELLED（可空=全部）")
    private String status;

    @Schema(description = "任务ID（可空）")
    private Long taskId;

    @Schema(description = "工序执行ID（可空）")
    private Long executionId;
}
