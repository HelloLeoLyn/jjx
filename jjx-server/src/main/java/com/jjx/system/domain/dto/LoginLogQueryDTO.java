package com.jjx.system.domain.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.jjx.common.core.page.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LoginLogQueryDTO extends PageQuery {

    private String username;
    private String loginIp;
    private Integer status;
    private String startDate;
    private String endDate;
    private String loginType;
    private boolean isAdmin=true;
}
