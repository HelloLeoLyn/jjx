package com.jjx.sales.domain.dto;

import com.jjx.common.core.page.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "样品单分页查询参数")
public class SampleOrderQueryDTO extends PageQuery {

    @Schema(description = "样品单号（模糊查询）")
    private String orderNo;

    @Schema(description = "客户ID")
    private Long customerId;

    @Schema(description = "客户名称（模糊查询）")
    private String customerName;

    @Schema(description = "客户简称（模糊查询）")
    private String customerShortName;

    @Schema(description = "样品单状态")
    private Integer sampleStatus;

    @Schema(description = "销售负责人ID")
    private Long salesPersonId;

    @Schema(description = "是否已由工程接单；不传表示全部")
    private Boolean hasAcceptor;
}
