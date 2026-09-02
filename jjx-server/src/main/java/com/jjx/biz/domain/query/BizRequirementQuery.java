package com.jjx.biz.domain.query;

import lombok.Data;

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
}
