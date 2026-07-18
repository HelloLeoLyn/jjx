package com.jjx.system.domain.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.jjx.common.core.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LogQueryDTO extends PageQuery {

    private String module;
    private String businessType;
    private Integer status;
    private String startDate;
    private String endDate;
    private String[] time;
}
