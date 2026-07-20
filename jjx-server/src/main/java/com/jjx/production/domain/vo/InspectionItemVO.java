package com.jjx.production.domain.vo;
import lombok.Data;
import java.util.List;
@Data
public class InspectionItemVO {
    private Long itemId;
    private String checkItem;
    private String standard;
    private String actualValue;
    private String result;
    private String remark;
    private List<InspectionItemVO> children;
}
