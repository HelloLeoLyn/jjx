package com.jjx.production.domain.dto;
import lombok.Data;
@Data
public class InspectionItemDTO {
    private Long itemId;
    private String checkItem;
    private String standard;
    private String actualValue;
    private String result;
    private String remark;
}
