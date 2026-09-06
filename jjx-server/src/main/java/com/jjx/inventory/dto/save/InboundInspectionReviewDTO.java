package com.jjx.inventory.dto.save;

import lombok.Data;

@Data
public class InboundInspectionReviewDTO {
    private Long approverId;
    private String approverName;
    private String remark;
}
