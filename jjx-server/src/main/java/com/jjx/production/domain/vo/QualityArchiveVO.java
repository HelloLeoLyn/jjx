package com.jjx.production.domain.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class QualityArchiveVO {
    private Long printLogId;
    private String recordNo;
    private String recordName;
    private String ownerDept;
    private Integer retentionYears;
    private String bizType;
    private Long bizId;
    private String operatorName;
    private LocalDateTime printTime;
    private LocalDate expiryDate;
    private Boolean archived;
}
