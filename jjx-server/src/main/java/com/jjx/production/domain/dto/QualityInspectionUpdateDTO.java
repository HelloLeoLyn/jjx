package com.jjx.production.domain.dto;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
@Data
public class QualityInspectionUpdateDTO {
    private Long inspectionId;
    private String result;
    /** P3-B：实际检验数量（DECIMAL(18,4)） */
    private BigDecimal totalQty;
    /** P3-B：质量认可合格数量（DECIMAL(18,4)） */
    private BigDecimal passQty;
    /** P3-B：质量判定不合格数量（DECIMAL(18,4)） */
    private BigDecimal failQty;
    private String defectDesc;
    private List<InspectionItemDTO> items;
}
