package com.jjx.production.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("production_trace_log")
public class ProductionTraceLog {
    @TableId(type = IdType.AUTO)
    private Long traceId;
    private String traceType;
    private String traceCode;
    private String batchNo;
    private Long orderId;
    private Long productId;
    private Long materialId;
    private String operation;
    private String operator;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime operateTime;
    private String detail;
    private String createBy;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
