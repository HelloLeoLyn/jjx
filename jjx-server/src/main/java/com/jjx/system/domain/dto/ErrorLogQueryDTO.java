package com.jjx.system.domain.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.jjx.common.core.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ErrorLogQueryDTO extends PageQuery {

    private String username;
    private String clientIP;
    private String exceptionName;
    private String startTime;
    private String endTime;
}
