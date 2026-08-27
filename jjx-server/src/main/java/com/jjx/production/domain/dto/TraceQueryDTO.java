package com.jjx.production.domain.dto;

import com.jjx.common.core.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class TraceQueryDTO extends PageQuery {
    private String traceCode;
    private String traceType;
    private String batchNo;
    private Long orderId;
    private String startTime;
    private String endTime;
}
