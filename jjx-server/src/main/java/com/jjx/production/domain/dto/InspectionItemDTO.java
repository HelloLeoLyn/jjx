package com.jjx.production.domain.dto;
import lombok.Data;
@Data
public class InspectionItemDTO {
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
}
