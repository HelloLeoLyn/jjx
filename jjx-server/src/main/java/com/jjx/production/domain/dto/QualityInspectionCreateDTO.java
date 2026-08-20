package com.jjx.production.domain.dto;
import lombok.Data;
import java.util.List;
@Data
public class QualityInspectionCreateDTO {
    private String inspectionType;
    private Long orderId;
    /** P3-B：关联工序执行（IPQC/FQC；IQC/OQC 可空） */
    private Long executionId;
    /** P3-B：关联报工（IPQC 可空/推荐；FQC=NULL；IQC/OQC=NULL） */
    private Long workReportId;
    private Long materialId;
    private Long productId;
    private String inspector;
    private String remark;
    private List<InspectionItemDTO> items;
}
