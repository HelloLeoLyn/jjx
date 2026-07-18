package com.jjx.system.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginDTO {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    /** 租户ID（多租户场景） */
    private Long tenantId;

    /** 验证码 */
    private String captcha;

    /** 验证码唯一标识 */
    private String captchaKey;

    /** 记住我 */
    private Boolean rememberMe = false;
}
