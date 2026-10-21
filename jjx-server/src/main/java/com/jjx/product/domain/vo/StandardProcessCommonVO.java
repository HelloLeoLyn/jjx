package com.jjx.product.domain.vo;

import lombok.Data;

@Data
public class StandardProcessCommonVO {
    private Long processId;
    private String processName;
    private String processCategory;
    private String icon;
    private Integer processStatus;
    private Long useCount;
}
