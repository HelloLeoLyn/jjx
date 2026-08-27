package com.jjx.production.domain.dto;
import com.jjx.common.core.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;
@Data
@EqualsAndHashCode(callSuper = true)
public class QualityInspectionQueryDTO extends PageQuery {
    private String inspectionNo;
    private String inspectionType;
    private Long orderId;
    /** P3-B：按工序执行过滤 */
    private Long executionId;
    /** P3-B：按报工过滤 */
    private Long workReportId;
    private String result;
}
