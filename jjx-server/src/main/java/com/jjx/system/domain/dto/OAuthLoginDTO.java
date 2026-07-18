// ==================== OAuthLoginDTO.java ====================
package com.jjx.system.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 第三方登录请求（钉钉/企业微信）
 */
@Data
public class OAuthLoginDTO {
    
    @NotBlank(message = "授权码不能为空")
    private String code;
    
    /** 登录类型：DING_TALK / WECHAT_WORK */
    @NotBlank(message = "登录类型不能为空")
    private String loginType;
    
    /** 租户ID */
    private Long tenantId;
    
    /** 企业ID（企业微信专用） */
    private String corpId;
}


