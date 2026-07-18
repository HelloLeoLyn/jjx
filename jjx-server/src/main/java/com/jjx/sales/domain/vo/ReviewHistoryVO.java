package com.jjx.sales.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@Schema(description = "审核历史VO")
public class ReviewHistoryVO {
    
    @Schema(description = "历史记录ID")
    private Long historyId;
    
    @Schema(description = "操作类型")
    private String actionType;
    
    @Schema(description = "操作类型名称")
    private String actionName;
    
    @Schema(description = "操作人")
    private String operatorName;
    
    @Schema(description = "操作时间")
    private LocalDateTime operateTime;
    
    @Schema(description = "操作备注")
    private String remark;
    
    @Schema(description = "操作结果")
    private String result;
}