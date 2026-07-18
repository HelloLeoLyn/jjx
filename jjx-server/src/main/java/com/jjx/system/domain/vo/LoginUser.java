package com.jjx.system.domain.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Set;
@Data
@Builder
public class LoginUser {
    private Long userId;
    private String userName;
    private String realName;
    private Long tenantId;
    private String token;
    private String tokenName;
    private Boolean isLogin;
    private List<String> roles;
    private Set<String> permissions;
    private Boolean needChangePassword;
    private Long loginTime;
    private Long deptId;
}
