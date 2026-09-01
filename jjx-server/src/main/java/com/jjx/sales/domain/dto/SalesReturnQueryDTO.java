package com.jjx.sales.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 销售退货单查询DTO
 */
@Data
public class SalesReturnQueryDTO {

    private String returnNo;

    private Long orderId;

    private String customerName;

    /** 退货状态：1申请中 2已审核 3已收货 4已退款 5已完成 6已取消 */
    private Integer returnStatus;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date returnDateStart;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date returnDateEnd;

    private Integer pageNum = 1;

    private Integer pageSize = 10;
}
