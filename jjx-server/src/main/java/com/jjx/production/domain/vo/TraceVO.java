package com.jjx.production.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TraceVO {
    private Long traceId;
    private String traceType;
    private String traceTypeName;
    private String traceCode;
    private String batchNo;
    private Long orderId;
    private String orderNo;
    private String productName;
    private String materialName;
    private String operation;
    private String operationName;
    private String operator;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime operateTime;
    private String detail;
    private String createBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
