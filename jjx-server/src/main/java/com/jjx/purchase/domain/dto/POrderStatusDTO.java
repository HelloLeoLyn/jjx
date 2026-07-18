package com.jjx.purchase.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
public class POrderStatusDTO {
    private Long orderId;
    private Integer currentStatus;
    private Integer targetStatus;
}
