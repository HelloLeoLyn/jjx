package com.jjx.production.domain.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class QualityInspectionVO {
    private Long inspectionId;
    private String inspectionNo;
    private String inspectionType;
    private String inspectionTypeName;
    private Long orderId;
    private String orderNo;
    /** P3-B：关联工序执行（IPQC/FQC；IQC/OQC 可空） */
    private Long executionId;
    /** P3-D：工序名称（展示，从 execution 带出） */
    private String processName;
    /** P3-B：关联报工（IPQC 可空/推荐；FQC=NULL；IQC/OQC=NULL） */
    private Long workReportId;
    private String materialName;
    private String productName;
    private String inspector;
    private LocalDateTime inspectTime;
    private String result;
    private String resultName;
    /** P3-B：实际检验数量（DECIMAL(18,4)） */
    private BigDecimal totalQty;
    /** P3-B：质量认可合格数量（DECIMAL(18,4)） */
    private BigDecimal passQty;
    /** P3-B：质量判定不合格数量（DECIMAL(18,4)） */
    private BigDecimal failQty;
    private String defectDesc;
    private String remark;
    private LocalDateTime createTime;
    private List<InspectionItemVO> items;
}
