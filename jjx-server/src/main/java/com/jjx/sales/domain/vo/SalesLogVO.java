package com.jjx.sales.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "操作日志VO")
public class SalesLogVO {
    
    @Schema(description = "日志ID")
    private Long logId;
    
    @Schema(description = "订单ID")
    private Long orderId;

    @Schema(description = "订单号")
    private String orderNo;
    
    @Schema(description = "操作类型")
    private Integer operationType;
    
    @Schema(description = "操作类型名称")
    private String operationTypeName;
    
    @Schema(description = "操作描述")
    private String operationDescription;
    
    @Schema(description = "操作人ID")
    private Long operatorId;
    
    @Schema(description = "操作人姓名")
    private String operatorName;
    
    @Schema(description = "操作时间")
    private LocalDateTime operationTime;
    
    @Schema(description = "操作结果")
    private Integer operationResult;
    
    @Schema(description = "操作结果名称")
    private String operationResultName;
    
    @Schema(description = "备注")
    private String remark;
}