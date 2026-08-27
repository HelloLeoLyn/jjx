package com.jjx.sales.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * 销售发货单查询DTO
 */
@Data
@Schema(description = "销售发货单查询DTO")
public class SalesDeliveryQueryDTO {

    @Schema(description = "销售订单ID")
    private Long orderId;

    @Schema(description = "发货单号")
    private String deliveryNo;

    @Schema(description = "客户名称")
    private String customerName;

    @Schema(description = "发货状态：1待发货 2已发货 3运输中 4已签收 5已拒收")
    private Integer deliveryStatus;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @Schema(description = "发货日期-起始")
    private Date deliveryDateStart;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @Schema(description = "发货日期-结束")
    private Date deliveryDateEnd;

    @Schema(description = "排序字段")
    private String orderByColumn;

    @Schema(description = "排序方式")
    private String isAsc = "desc";

    @Schema(description = "页码")
    private Integer pageNum = 1;

    @Schema(description = "每页大小")
    private Integer pageSize = 10;
}
