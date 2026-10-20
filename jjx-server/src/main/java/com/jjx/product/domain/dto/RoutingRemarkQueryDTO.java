package com.jjx.product.domain.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

/** 仅查询路线父行备注，不包含作业说明或子作业备注。 */
@Data
public class RoutingRemarkQueryDTO {
    @Size(max = 64)
    private String processCategory;
    @Size(max = 100)
    private List<Long> processIds;
    @Size(max = 500)
    private String keyword;
}
