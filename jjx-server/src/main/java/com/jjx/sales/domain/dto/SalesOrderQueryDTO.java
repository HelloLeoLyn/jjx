package com.jjx.sales.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Date;

/**
 * 销售订单查询DTO
 */
@Data
@Schema(description = "销售订单查询DTO")
public class SalesOrderQueryDTO {

    @Schema(description = "订单编号", example = "SO202604190001")
    private String orderNo;

    @Schema(description = "客户ID", example = "1001")
    private Long customerId;

    @Schema(description = "客户名称（模糊查询）", example = "科技")
    private String customerName;

    @Schema(description = "订单类型", allowableValues = {"1", "2"}, example = "1")
    private Integer orderType;

    @Schema(description = "订单状态", allowableValues = {"1", "2", "3", "4", "5", "6", "7"})
    private Integer orderStatus;

    @Schema(description = "生产状态", allowableValues = {"1", "2", "3", "4"})
    private Integer prodStatus;

    @Schema(description = "支付状态", allowableValues = {"1", "2", "3", "4", "5"})
    private Integer paymentStatus;

    @Schema(description = "是否急单", allowableValues = {"0", "1"})
    private Integer isUrgent;

    @Schema(description = "销售负责人ID", example = "1001")
    private Long salesManagerId;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @Schema(description = "订单开始日期", example = "2026-04-01")
    private Date orderDateStart;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @Schema(description = "订单结束日期", example = "2026-04-30")
    private Date orderDateEnd;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @Schema(description = "交货开始日期", example = "2026-05-01")
    private Date deliveryDateStart;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @Schema(description = "交货结束日期", example = "2026-05-31")
    private Date deliveryDateEnd;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @Schema(description = "创建时间开始日期", example = "2026-05-01")
    private Date createTimeStart;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @Schema(description = "创建时间结束日期", example = "2026-05-31")
    private Date createTimeEnd;

    @Schema(description = "排序字段", example = "createTime",
            allowableValues = {"orderId", "orderNo", "orderDate", "deliveryDate",
                    "totalAmount", "finalAmount", "createTime", "updateTime"})
    private String orderByColumn;

    @Schema(description = "排序方式", example = "asc", allowableValues = {"asc", "desc"})
    private String isAsc = "desc";

    @Schema(description = "页码", example = "1")
    private Integer pageNum = 1;

    @Schema(description = "每页大小", example = "10")
    private Integer pageSize = 10;
}
