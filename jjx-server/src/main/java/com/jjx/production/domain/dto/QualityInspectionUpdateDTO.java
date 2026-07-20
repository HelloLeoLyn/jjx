package com.jjx.production.domain.dto;
import lombok.Data;
import java.util.List;
@Data
public class QualityInspectionUpdateDTO {
    private Long inspectionId;
    private String result;
    private Integer totalQty;
    private Integer passQty;
    private Integer failQty;
    private String defectDesc;
    private List<InspectionItemDTO> items;
}
