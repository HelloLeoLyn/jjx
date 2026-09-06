package com.jjx.production.domain.vo;
import lombok.Data;
import java.util.List;
@Data
public class InspectionItemVO {
    private Long itemId;
    private String checkItem;
    private String standard;
    private String inspectionMethod;
    private String equipment;
    private String actualValue;
    private String result;
    private java.math.BigDecimal crQuantity;
    private java.math.BigDecimal maQuantity;
    private java.math.BigDecimal miQuantity;
    private String remark;
    private List<InspectionItemVO> children;
}
