package com.jjx.biz.domain.query;

import lombok.Data;

import java.time.LocalDate;

/**
 * 需求单查询参数
 */
@Data
public class BizRequirementQuery {
    private String requirementNo;
    private String requirementType;
    private Integer requirementStatus;
    private String title;
    private String source;
    private String bizNo;
    private String changeType;
    private LocalDate startDate;
    private LocalDate endDate;
}
