package com.jjx.inventory.dto.save;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class IqcQuarantineActionDTO {
    private String action;
    private BigDecimal quantity;
    private Long operatorId;
    private String operatorName;
    private String remark;
}
