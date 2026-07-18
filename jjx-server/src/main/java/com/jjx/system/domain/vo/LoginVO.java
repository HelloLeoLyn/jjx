package com.jjx.system.domain.vo;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Set;

/**
 * 登录响应类
 *
 * @author system
 */
@Data
@NoArgsConstructor
public class LoginVO {
    private Long userId;
    private String token;
    private Boolean isLogin;
    private List<String> roles;
    private Set<String> permissions;
    private Long loginTime;
    private LoginUser userInfo;
}
