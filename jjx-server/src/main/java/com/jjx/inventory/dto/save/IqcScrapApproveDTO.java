package com.jjx.inventory.dto.save;

import lombok.Data;

@Data
public class IqcScrapApproveDTO {
    private Long approverId;
    private String approverName;
    private String remark;
    private boolean approved;
}
