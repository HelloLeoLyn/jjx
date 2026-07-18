package com.jjx.sales.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@Schema(description = "审核状态VO")
public class ReviewStatusVO {
    
    @Schema(description = "订单ID")
    private Long orderId;
    
    @Schema(description = "订单编号")
    private String orderNo;
    
    @Schema(description = "订单状态")
    private Integer orderStatus;
    
    @Schema(description = "订单状态名称")
    private String orderStatusName;
    
    @Schema(description = "审核人ID")
    private Long reviewerId;
    
    @Schema(description = "审核人姓名")
    private String reviewerName;
    
    @Schema(description = "审核开始时间")
    private LocalDateTime reviewStartTime;
    
    @Schema(description = "审核结束时间")
    private LocalDateTime reviewEndTime;
    
    @Schema(description = "审核备注")
    private String reviewRemark;
    
    @Schema(description = "驳回原因")
    private String rejectReason;
}