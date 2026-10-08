package com.jjx.sales.domain.dto;
import lombok.Data;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
@Data
public class DeliveryArrangeQueryDTO {
    private String keyword;
    private Long orderId;
    private Long customerId;
    @Min(1) private Integer pageNum = 1;
    @Min(1) @Max(200) private Integer pageSize = 30;
}
