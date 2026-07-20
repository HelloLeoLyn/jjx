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
    private String result;
}
