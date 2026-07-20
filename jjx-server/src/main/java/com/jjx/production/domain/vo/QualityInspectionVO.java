package com.jjx.production.domain.vo;

import lombok.Data;
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
    private String materialName;
    private String productName;
    private String inspector;
    private LocalDateTime inspectTime;
    private String result;
    private String resultName;
    private Integer totalQty;
    private Integer passQty;
    private Integer failQty;
    private String defectDesc;
    private String remark;
    private LocalDateTime createTime;
    private List<InspectionItemVO> items;
}
