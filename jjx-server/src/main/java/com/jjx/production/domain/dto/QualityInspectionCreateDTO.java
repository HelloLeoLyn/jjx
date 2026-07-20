package com.jjx.production.domain.dto;
import lombok.Data;
import java.util.List;
@Data
public class QualityInspectionCreateDTO {
    private String inspectionType;
    private Long orderId;
    private Long materialId;
    private Long productId;
    private String inspector;
    private String remark;
    private List<InspectionItemDTO> items;
}
