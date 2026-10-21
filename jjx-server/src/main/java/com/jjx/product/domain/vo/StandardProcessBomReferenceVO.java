package com.jjx.product.domain.vo;

import lombok.Data;

@Data
public class StandardProcessBomReferenceVO {
    private Long bomId;
    private Long itemId;
    private Long processId;
    private String processName;
}
